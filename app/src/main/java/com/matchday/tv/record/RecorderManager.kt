package com.matchday.tv.record

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import androidx.compose.runtime.mutableStateListOf
import com.matchday.tv.data.XtreamClient
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
import java.util.concurrent.TimeUnit

/** One place a recording can be fetched from: a channel version on some subscription. */
data class RecSource(val url: String, val userAgent: String, val label: String, val subId: String?)

/** A continuous run of recorded video. Gaps fall between parts. */
data class RecPart(val file: String, val startedAt: Long, val bytes: Long, val complete: Boolean, val source: String)

/** What the UI shows for a recording (an immutable snapshot). */
data class RecordingInfo(
    val id: String,
    val dir: File,
    val title: String,
    val channel: String,
    val startedAt: Long,
    val endAt: Long,
    val finishedAt: Long,
    /** recording, complete, stopped, failed */
    val status: String,
    val parts: List<RecPart>,
    val gaps: List<Pair<Long, Long>>,
    val notes: List<String>,
) {
    val active get() = status == "recording"
    val bytes get() = parts.sumOf { it.bytes }
    val gapMs get() = gaps.sumOf { it.second - it.first }
    val sourcesUsed get() = parts.map { it.source }.distinct()
}

/**
 * Records live streams so a glitch doesn't end the recording: video is written in parts, a stalled
 * or dropped stream is retried, then the next version, then the same channel on other
 * subscriptions, until the programme's end time. Runs on its own threads; a foreground service
 * keeps the app alive while anything is recording.
 */
object RecorderManager {
    val recordings = mutableStateListOf<RecordingInfo>()
    private val jobs = HashMap<String, Job>()
    private val main = Handler(Looper.getMainLooper())
    private lateinit var appContext: Context
    private var loaded = false

    private val http = XtreamClient.http.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS) // no data for 10s counts as a drop
        .build()

    const val BYTES_PER_HOUR = 3L * 1024 * 1024 * 1024
    private const val MIN_FREE = 300L * 1024 * 1024

    fun init(context: Context) {
        appContext = context.applicationContext
        if (!loaded) {
            loaded = true
            reload()
        }
    }

    // ---------- Storage ----------

    data class Volume(val dir: File, val removable: Boolean) {
        val label get() = if (removable) "USB / SD card" else "This device"
        val freeBytes: Long get() = runCatching { StatFs(dir.path).availableBytes }.getOrDefault(0)
    }

    /** This device plus any USB drive or SD card Android has mounted. */
    fun volumes(): List<Volume> =
        appContext.getExternalFilesDirs("recordings").filterNotNull().mapNotNull { d ->
            runCatching {
                d.mkdirs()
                Volume(d, android.os.Environment.isExternalStorageRemovable(d))
            }.getOrNull()
        }

    fun reload() {
        val found = ArrayList<RecordingInfo>()
        for (v in volumes()) {
            v.dir.listFiles()?.filter { it.isDirectory }?.forEach { dir ->
                readInfo(dir)?.let { info ->
                    // Anything marked as recording but not running here was cut short by a restart.
                    found += if (info.active && !jobs.containsKey(info.id)) info.copy(status = "stopped", notes = info.notes + "Stopped: the app or device restarted") else info
                }
            }
        }
        recordings.clear()
        recordings.addAll(found.sortedByDescending { it.startedAt })
    }

    fun get(id: String): RecordingInfo? = recordings.firstOrNull { it.id == id }

    fun partFile(id: String, index: Int): File? = get(id)?.let { r -> r.parts.getOrNull(index)?.let { File(r.dir, it.file) } }

    fun isGrowing(id: String, index: Int): Boolean = get(id)?.let { r -> r.active && r.parts.getOrNull(index)?.complete == false } ?: false

    // ---------- Recording ----------

    fun start(title: String, channel: String, sources: List<RecSource>, endAt: Long, volume: Volume): String {
        val id = System.currentTimeMillis().toString(36)
        val dir = File(volume.dir, "rec_$id").apply { mkdirs() }
        val info = RecordingInfo(id, dir, title, channel, System.currentTimeMillis(), endAt, 0, "recording", emptyList(), emptyList(), emptyList())
        recordings.add(0, info)
        writeInfo(info)
        val job = Job(info, sources)
        jobs[id] = job
        RecordingService.update(appContext)
        job.start()
        return id
    }

    fun stop(id: String) {
        jobs[id]?.requestStop()
    }

    fun delete(id: String) {
        stop(id)
        val r = get(id) ?: return
        recordings.remove(r)
        Thread { r.dir.deleteRecursively() }.start()
    }

    fun activeCount() = jobs.size
    fun activeTitles() = jobs.values.map { it.title }

    private fun publish(info: RecordingInfo) {
        main.post {
            val i = recordings.indexOfFirst { it.id == info.id }
            if (i >= 0) recordings[i] = info else recordings.add(0, info)
        }
    }

    private fun finished(id: String) {
        main.post {
            jobs.remove(id)
            RecordingService.update(appContext)
        }
    }

    private class Job(start: RecordingInfo, val sources: List<RecSource>) : Thread("shedtv-record") {
        @Volatile private var info = start
        @Volatile private var stopped = false
        @Volatile private var call: Call? = null
        val title get() = info.title

        fun requestStop() {
            stopped = true
            runCatching { call?.cancel() }
        }

        private fun note(text: String) {
            val t = java.text.SimpleDateFormat("HH:mm", java.util.Locale.UK).format(java.util.Date())
            info = info.copy(notes = (info.notes + "$t $text").takeLast(30))
        }

        private fun done() = stopped || System.currentTimeMillis() >= info.endAt

        override fun run() {
            var idx = 0
            var fails = 0
            var gapStart: Long? = null
            var lastPublish = 0L
            var partIndex = 0
            if (sources.isEmpty()) note("No source to record from")
            while (!done() && sources.isNotEmpty()) {
                val src = sources[idx]
                var out: FileOutputStream? = null
                var part: RecPart? = null
                try {
                    val c = http.newCall(Request.Builder().url(src.url).header("User-Agent", src.userAgent).build())
                    call = c
                    c.execute().use { resp ->
                        if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                        val type = resp.header("Content-Type").orEmpty()
                        if ("text/html" in type || "json" in type) throw IOException("provider sent a web page, not video")
                        val input = resp.body?.byteStream() ?: throw IOException("empty reply")
                        val buf = ByteArray(188 * 348)
                        var sinceCheck = 0L
                        while (!done()) {
                            val n = input.read(buf)
                            if (n < 0) throw IOException("stream ended")
                            if (n == 0) continue
                            if (part == null) {
                                val now = System.currentTimeMillis()
                                gapStart?.let { g -> info = info.copy(gaps = info.gaps + (g to now)) }
                                gapStart = null
                                fails = 0
                                partIndex++
                                val p = RecPart("part$partIndex.ts", now, 0, false, src.label)
                                out = FileOutputStream(File(info.dir, p.file))
                                part = p
                                info = info.copy(parts = info.parts + p)
                                note(if (partIndex == 1) "Recording from ${src.label}" else "Back on ${src.label}")
                            }
                            out!!.write(buf, 0, n)
                            part = part!!.copy(bytes = part!!.bytes + n)
                            info = info.copy(parts = info.parts.dropLast(1) + part!!)
                            sinceCheck += n
                            if (sinceCheck > 8L * 1024 * 1024) {
                                sinceCheck = 0
                                val free = runCatching { StatFs(info.dir.path).availableBytes }.getOrDefault(Long.MAX_VALUE)
                                if (free < MIN_FREE) {
                                    note("Stopped: storage nearly full")
                                    stopped = true
                                }
                            }
                            val now = System.currentTimeMillis()
                            if (now - lastPublish > 2_000) {
                                lastPublish = now
                                publish(info)
                                if (now % 10_000 < 2_100) writeInfo(info)
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (!done()) {
                        if (gapStart == null) gapStart = System.currentTimeMillis()
                        note("Lost ${src.label}: ${e.message ?: "error"}")
                        fails++
                    }
                } finally {
                    runCatching { out?.close() }
                    part?.let { p -> info = info.copy(parts = info.parts.dropLast(1) + p.copy(complete = true)) }
                    publish(info)
                    writeInfo(info)
                }
                if (done()) break
                // Retry the same source a few times, then move on to the next one (and round again).
                if (fails >= 3) {
                    fails = 0
                    idx = (idx + 1) % sources.size
                    note("Trying ${sources[idx].label}")
                    if (idx == 0) pause(10_000)
                } else {
                    pause(longArrayOf(2_000, 5_000, 10_000)[(fails - 1).coerceIn(0, 2)])
                }
            }
            gapStart?.let { g -> if (info.parts.isNotEmpty()) info = info.copy(gaps = info.gaps + (g to System.currentTimeMillis())) }
            val status = when {
                info.parts.isEmpty() -> "failed"
                stopped && System.currentTimeMillis() < info.endAt -> "stopped"
                else -> "complete"
            }
            info = info.copy(status = status, finishedAt = System.currentTimeMillis(), parts = info.parts.map { it.copy(complete = true) })
            publish(info)
            writeInfo(info)
            finished(info.id)
        }

        private fun pause(ms: Long) {
            val until = System.currentTimeMillis() + ms
            while (!done() && System.currentTimeMillis() < until) sleep(250)
        }
    }

    // ---------- Persistence (info.json in each recording's folder) ----------

    private fun writeInfo(r: RecordingInfo) {
        val o = buildJsonObject {
            put("id", r.id); put("title", r.title); put("channel", r.channel)
            put("startedAt", r.startedAt); put("endAt", r.endAt); put("finishedAt", r.finishedAt); put("status", r.status)
            put("parts", buildJsonArray {
                r.parts.forEach { p ->
                    add(buildJsonObject {
                        put("file", p.file); put("startedAt", p.startedAt); put("bytes", p.bytes); put("complete", p.complete); put("source", p.source)
                    })
                }
            })
            put("gaps", buildJsonArray { r.gaps.forEach { (a, b) -> add(buildJsonArray { add(JsonPrimitive(a)); add(JsonPrimitive(b)) }) } })
            put("notes", buildJsonArray { r.notes.forEach { add(JsonPrimitive(it)) } })
        }
        runCatching {
            val tmp = File(r.dir, "info.json.tmp")
            tmp.writeText(o.toString())
            tmp.renameTo(File(r.dir, "info.json"))
        }
    }

    private fun readInfo(dir: File): RecordingInfo? = runCatching {
        val o = Json.parseToJsonElement(File(dir, "info.json").readText()) as JsonObject
        fun s(k: String) = (o[k] as? JsonPrimitive)?.contentOrNull
        RecordingInfo(
            id = s("id")!!, dir = dir, title = s("title").orEmpty(), channel = s("channel").orEmpty(),
            startedAt = s("startedAt")?.toLongOrNull() ?: 0, endAt = s("endAt")?.toLongOrNull() ?: 0,
            finishedAt = s("finishedAt")?.toLongOrNull() ?: 0, status = s("status") ?: "complete",
            parts = (o["parts"] as? JsonArray).orEmpty().mapNotNull { e ->
                val p = e as? JsonObject ?: return@mapNotNull null
                fun ps(k: String) = (p[k] as? JsonPrimitive)?.contentOrNull
                RecPart(ps("file") ?: return@mapNotNull null, ps("startedAt")?.toLongOrNull() ?: 0, ps("bytes")?.toLongOrNull() ?: 0, true, ps("source").orEmpty())
            },
            gaps = (o["gaps"] as? JsonArray).orEmpty().mapNotNull { e ->
                val a = e as? JsonArray ?: return@mapNotNull null
                val x = (a.getOrNull(0) as? JsonPrimitive)?.contentOrNull?.toLongOrNull() ?: return@mapNotNull null
                val y = (a.getOrNull(1) as? JsonPrimitive)?.contentOrNull?.toLongOrNull() ?: return@mapNotNull null
                x to y
            },
            notes = (o["notes"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull },
        )
    }.getOrNull()
}
