package com.matchday.tv.download

import android.content.Context
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import android.provider.DocumentsContract
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.matchday.tv.data.XtreamClient
import com.matchday.tv.record.RecordingService
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit

/** A film or episode saved for watching offline. */
data class DownloadItem(
    val id: String,
    /** "movie" or "episode". */
    val kind: String,
    /** Resume-point id, matching the online library's. */
    val historyId: String,
    val parentId: String?,
    val title: String,
    val subtitle: String,
    val image: String?,
    val season: Int,
    val episode: Int,
    val url: String,
    val userAgent: String,
    val subId: String?,
    val fileName: String,
    /** Where it's saved: a file path, or a content:// address in a folder the user picked. */
    val location: String,
    val totalBytes: Long,
    val doneBytes: Long,
    /** queued, downloading, paused, done, failed */
    val status: String,
    val message: String,
    val position: Long,
    val duration: Long,
    val added: Long,
) {
    val progress get() = if (totalBytes <= 0) 0f else (doneBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
    val playUri: String get() = if (location.startsWith("content://")) location else Uri.fromFile(File(location)).toString()
}

/**
 * Downloads films and episodes one at a time. A dropped connection is retried and carries on
 * from where it stopped (HTTP range requests), so a long download survives Wi-Fi blips.
 */
object DownloadManager {
    val items = mutableStateListOf<DownloadItem>()
    /** Shown in the Downloads screen: what the downloader is waiting for, if anything. */
    var waiting by mutableStateOf<String?>(null)
        private set
    /** Subscription whose single connection is in use for watching: its downloads wait. */
    @Volatile var holdSub: String? = null
    @Volatile var wifiOnly = true

    private lateinit var ctx: Context
    private val main = Handler(Looper.getMainLooper())
    private val lock = Object()
    private var worker: Thread? = null
    @Volatile private var call: Call? = null
    @Volatile private var currentId: String? = null

    private val http = XtreamClient.http.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun init(context: Context) {
        if (::ctx.isInitialized) return
        ctx = context.applicationContext
        load()
        kick()
    }

    val busy: Boolean get() = items.any { it.status == "queued" || it.status == "downloading" }
    fun activeTitle(): String? = items.firstOrNull { it.status == "downloading" }?.let { it.title + if (it.kind == "episode") " " + it.subtitle.substringBefore(" · ") else "" }

    fun find(historyId: String): DownloadItem? = items.firstOrNull { it.historyId == historyId }
    fun get(id: String): DownloadItem? = items.firstOrNull { it.id == id }

    // ---------- Locations ----------

    data class Volume(val dir: File, val removable: Boolean) {
        val label get() = if (removable) "USB / SD card" else "This device"
        val freeBytes: Long get() = runCatching { StatFs(dir.path).availableBytes }.getOrDefault(0)
    }

    fun volumes(): List<Volume> =
        ctx.getExternalFilesDirs("downloads").filterNotNull().mapNotNull { d ->
            runCatching { d.mkdirs(); Volume(d, android.os.Environment.isExternalStorageRemovable(d)) }.getOrNull()
        }

    /** Free space where [location] (a folder path or a picked folder) lives; null when unknown. */
    fun freeBytes(location: String): Long? =
        if (location.startsWith("content://")) null else runCatching { StatFs(location).availableBytes }.getOrNull()

    fun folderName(tree: String): String =
        runCatching { DocumentsContract.getTreeDocumentId(Uri.parse(tree)).substringAfterLast(':').ifBlank { "Chosen folder" } }.getOrDefault("Chosen folder")

    // ---------- Queue ----------

    /** Add to the queue. [folder] is a folder path or a content:// tree the user picked. */
    fun add(item: DownloadItem, folder: String) {
        if (items.any { it.historyId == item.historyId }) return
        val name = safeName(item.fileName)
        val location = if (folder.startsWith("content://")) folder else File(folder, name).path
        items.add(item.copy(fileName = name, location = location, status = "queued", added = System.currentTimeMillis()))
        save()
        kick()
    }

    fun pause(id: String) {
        update(id) { it.copy(status = "paused", message = "") }
        if (currentId == id) runCatching { call?.cancel() }
        save()
    }

    fun resume(id: String) {
        update(id) { it.copy(status = "queued", message = "") }
        save()
        kick()
    }

    fun delete(id: String) {
        val it = get(id) ?: return
        if (currentId == id) runCatching { call?.cancel() }
        items.remove(it)
        save()
        Thread {
            synchronized(lock) { removeFile(it) }
        }.start()
        RecordingService.update(ctx)
    }

    fun savePosition(historyId: String, position: Long, duration: Long) {
        val i = items.indexOfFirst { it.historyId == historyId }
        if (i >= 0) { items[i] = items[i].copy(position = position, duration = duration); save() }
    }

    /** Start the downloader if it isn't running. */
    fun kick() {
        synchronized(lock) {
            if (worker?.isAlive == true) { (lock as Object).notifyAll(); return }
            if (!items.any { it.status == "queued" || it.status == "downloading" }) return
            worker = Thread(::runQueue, "shedtv-download").apply { start() }
        }
        main.post { RecordingService.update(ctx) }
    }

    /** Call when the watched subscription changes (or watching stops). */
    fun hold(subId: String?) {
        holdSub = subId
        if (subId != null && currentSub() == subId) runCatching { call?.cancel() }
        if (subId == null) kick()
    }

    private fun currentSub() = currentId?.let { id -> get(id)?.subId }

    private fun update(id: String, f: (DownloadItem) -> DownloadItem) {
        val run = Runnable {
            val i = items.indexOfFirst { it.id == id }
            if (i >= 0) items[i] = f(items[i])
        }
        if (Looper.myLooper() == Looper.getMainLooper()) run.run() else main.post(run)
    }

    private fun snapshot(): List<DownloadItem> {
        if (Looper.myLooper() == Looper.getMainLooper()) return items.toList()
        val r = java.util.concurrent.FutureTask { items.toList() }
        main.post(r)
        return r.get()
    }

    private fun setWaiting(text: String?) = main.post { waiting = text }

    private fun onWifiOrUnmetered(): Boolean {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return cm.activeNetwork != null && !cm.isActiveNetworkMetered
    }

    private fun online(): Boolean {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return cm.activeNetwork != null
    }

    private fun runQueue() {
        var fails = 0
        var lastId: String? = null
        while (true) {
            val all = snapshot()
            val next = all.firstOrNull { it.status == "downloading" } ?: all.firstOrNull { it.status == "queued" && (holdSub == null || it.subId != holdSub) }
            if (next == null) {
                val held = all.any { it.status == "queued" }
                if (!held) break
                setWaiting("Paused while you watch (your subscription allows 1 connection)")
                nap(3_000)
                continue
            }
            if (!online()) { setWaiting("Waiting for an internet connection"); nap(5_000); continue }
            if (wifiOnly && !onWifiOrUnmetered()) { setWaiting("Waiting for Wi-Fi (Settings → Downloads to change)"); nap(5_000); continue }
            if (next.subId != null && next.subId == holdSub) { setWaiting("Paused while you watch (your subscription allows 1 connection)"); nap(3_000); continue }
            setWaiting(null)
            if (next.id != lastId) { fails = 0; lastId = next.id }
            currentId = next.id
            update(next.id) { it.copy(status = "downloading", message = "") }
            val result = try {
                fetch(next)
                "done"
            } catch (e: Exception) {
                e.message ?: "error"
            } finally {
                currentId = null
            }
            val now = snapshot().firstOrNull { it.id == next.id }
            if (result == "done") {
                fails = 0
                update(next.id) { it.copy(status = "done", message = "", doneBytes = it.totalBytes.coerceAtLeast(it.doneBytes), totalBytes = it.totalBytes.coerceAtLeast(it.doneBytes)) }
            } else if (now == null || now.status == "paused") {
                // paused or deleted by the user
            } else if (next.subId != null && next.subId == holdSub) {
                // stopped for watching; picks up again afterwards
            } else if (result.startsWith("!")) {
                update(next.id) { it.copy(status = "failed", message = result.drop(1)) }
            } else {
                fails++
                if (fails > 30) {
                    update(next.id) { it.copy(status = "failed", message = "Kept failing: $result") }
                } else {
                    update(next.id) { it.copy(message = "Connection lost, retrying ($result)") }
                    nap(longArrayOf(3_000, 5_000, 10_000, 20_000, 30_000)[(fails - 1).coerceAtMost(4)])
                }
            }
            save()
        }
        setWaiting(null)
        synchronized(lock) { worker = null }
        main.post { RecordingService.update(ctx) }
    }

    private fun nap(ms: Long) {
        synchronized(lock) { runCatching { (lock as Object).wait(ms) } }
    }

    /** Download (or carry on downloading) one item. Errors starting with "!" are final. */
    private fun fetch(item: DownloadItem) {
        var start = item.doneBytes
        val req = Request.Builder().url(item.url).header("User-Agent", item.userAgent)
        if (start > 0) req.header("Range", "bytes=$start-")
        val c = http.newCall(req.build())
        call = c
        c.execute().use { resp ->
            if (resp.code == 416 && item.totalBytes > 0 && start >= item.totalBytes) return
            if (resp.code in 400..499 && resp.code != 408 && resp.code != 429) throw IOException("!The provider refused this download (HTTP ${resp.code})")
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val type = resp.header("Content-Type").orEmpty()
            if ("text/html" in type || "json" in type) throw IOException("provider sent a web page, not video")
            if (start > 0 && resp.code != 206) start = 0 // provider can't resume: start again
            val body = resp.body ?: throw IOException("empty reply")
            val total = if (body.contentLength() > 0) start + body.contentLength() else item.totalBytes
            freeBytes(File(item.location).parent ?: item.location)?.let { free ->
                if (total > 0 && total - start > free - 200L * 1024 * 1024) throw IOException("!Not enough space (needs ${gb(total - start)}, ${gb(free)} free)")
            }
            update(item.id) { it.copy(totalBytes = total) }
            val (out, location) = synchronized(lock) { openOutput(item, start) }
            if (location != item.location) update(item.id) { it.copy(location = location) }
            out.use { o ->
                val input = body.byteStream()
                val buf = ByteArray(256 * 1024)
                var done = start
                var lastUi = 0L
                var lastSave = System.currentTimeMillis()
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    o.write(buf, 0, n)
                    done += n
                    val t = System.currentTimeMillis()
                    if (t - lastUi > 1_000) {
                        lastUi = t
                        val d = done
                        update(item.id) { it.copy(doneBytes = d, message = "") }
                        if (t - lastSave > 10_000) { lastSave = t; o.flush(); save() }
                    }
                }
                o.flush()
                val d = done
                update(item.id) { it.copy(doneBytes = d) }
                if (total > 0 && done < total) throw IOException("stream ended early")
            }
        }
    }

    /** Open the file for writing at [start], creating it if needed. Returns the stream and its location. */
    private fun openOutput(item: DownloadItem, start: Long): Pair<OutputStream, String> {
        if (!item.location.startsWith("content://")) {
            val f = File(item.location)
            f.parentFile?.mkdirs()
            val raf = RandomAccessFile(f, "rw")
            raf.setLength(start)
            raf.seek(start)
            return FileOutputStream(raf.fd).let { fos ->
                object : OutputStream() {
                    override fun write(b: Int) = fos.write(b)
                    override fun write(b: ByteArray, off: Int, len: Int) = fos.write(b, off, len)
                    override fun flush() = fos.flush()
                    override fun close() { runCatching { fos.close() }; raf.close() }
                }
            } to f.path
        }
        val cr = ctx.contentResolver
        val uri = Uri.parse(item.location)
        // A picked folder (tree) means the file doesn't exist yet: create it inside.
        val doc = if (DocumentsContract.isTreeUri(uri) && !isDocumentInTree(uri)) {
            val parent = DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri))
            DocumentsContract.createDocument(cr, parent, "application/octet-stream", item.fileName)
                ?: throw IOException("!Couldn't create the file in the chosen folder")
        } else uri
        val pfd = cr.openFileDescriptor(doc, "rw") ?: throw IOException("!Couldn't open the file")
        val fos = FileOutputStream(pfd.fileDescriptor)
        fos.channel.truncate(start)
        fos.channel.position(start)
        return object : OutputStream() {
            override fun write(b: Int) = fos.write(b)
            override fun write(b: ByteArray, off: Int, len: Int) = fos.write(b, off, len)
            override fun flush() = fos.flush()
            override fun close() { runCatching { fos.close() }; pfd.close() }
        } to doc.toString()
    }

    private fun isDocumentInTree(uri: Uri) = runCatching { DocumentsContract.getDocumentId(uri); uri.pathSegments.size >= 4 }.getOrDefault(false)

    private fun removeFile(item: DownloadItem) {
        runCatching {
            if (item.location.startsWith("content://")) {
                val uri = Uri.parse(item.location)
                if (isDocumentInTree(uri)) DocumentsContract.deleteDocument(ctx.contentResolver, uri)
            } else File(item.location).delete()
        }
    }

    fun gb(bytes: Long) = if (bytes >= 1024L * 1024 * 1024) "%.1f GB".format(bytes / 1073741824.0) else "%d MB".format(bytes / 1048576)

    private fun safeName(name: String) = name.replace(Regex("[\\\\/:*?\"<>|]"), " ").replace(Regex("\\s+"), " ").trim().take(150)

    // ---------- Saved list (app-private, so it survives whatever folder is used) ----------

    private fun file() = File(ctx.filesDir, "downloads.json")

    private val saveLock = Object()

    private fun save() {
        val list = snapshot()
        synchronized(saveLock) { write(list) }
    }

    private fun write(list: List<DownloadItem>) {
        val arr = buildJsonArray {
            list.forEach { d ->
                add(buildJsonObject {
                    put("id", d.id); put("kind", d.kind); put("historyId", d.historyId); d.parentId?.let { put("parentId", it) }
                    put("title", d.title); put("subtitle", d.subtitle); d.image?.let { put("image", it) }
                    put("season", d.season); put("episode", d.episode); put("url", d.url); put("userAgent", d.userAgent)
                    d.subId?.let { put("subId", it) }; put("fileName", d.fileName); put("location", d.location)
                    put("totalBytes", d.totalBytes); put("doneBytes", d.doneBytes); put("status", d.status); put("message", d.message)
                    put("position", d.position); put("duration", d.duration); put("added", d.added)
                })
            }
        }
        runCatching {
            val tmp = File(ctx.filesDir, "downloads.json.tmp")
            tmp.writeText(arr.toString())
            tmp.renameTo(file())
        }
    }

    private fun load() {
        val arr = runCatching { Json.parseToJsonElement(file().readText()) as JsonArray }.getOrNull() ?: return
        items.clear()
        arr.forEach { el ->
            val o = el as? JsonObject ?: return@forEach
            fun s(k: String) = (o[k] as? JsonPrimitive)?.contentOrNull
            fun l(k: String) = s(k)?.toLongOrNull() ?: 0L
            val status = s("status") ?: "queued"
            items += DownloadItem(
                id = s("id") ?: return@forEach, kind = s("kind") ?: "movie", historyId = s("historyId").orEmpty(), parentId = s("parentId"),
                title = s("title").orEmpty(), subtitle = s("subtitle").orEmpty(), image = s("image"),
                season = s("season")?.toIntOrNull() ?: 0, episode = s("episode")?.toIntOrNull() ?: 0,
                url = s("url").orEmpty(), userAgent = s("userAgent").orEmpty(), subId = s("subId"),
                fileName = s("fileName").orEmpty(), location = s("location").orEmpty(),
                totalBytes = l("totalBytes"), doneBytes = l("doneBytes"),
                status = if (status == "downloading") "queued" else status, message = "",
                position = l("position"), duration = l("duration"), added = l("added"),
            )
        }
    }
}
