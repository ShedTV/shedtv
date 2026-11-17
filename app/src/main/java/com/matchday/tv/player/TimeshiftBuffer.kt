package com.matchday.tv.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import com.matchday.tv.data.UserAgents
import com.matchday.tv.data.XtreamClient
import okhttp3.Call
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InterruptedIOException
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit

/**
 * Live rewind. The live stream is downloaded once, into short time-stamped chunk files,
 * and the player reads from those files instead of the network. Pausing just means the
 * player stops reading while the download carries on; rewinding means reading again from
 * an older chunk. Only one connection to the provider is ever open.
 */
class TimeshiftBuffer(private val root: File) {

    class Chunk(val index: Long, val file: File, val startedAt: Long) {
        @Volatile var size = 0L
        @Volatile var complete = false
    }

    class Session(val url: String, val dir: File, val maxBytes: Long) {
        val lock = Object()
        val chunks = ArrayList<Chunk>()
        var nextIndex = 0L
        var totalBytes = 0L
        @Volatile var stopped = false
        @Volatile var failure: IOException? = null
        @Volatile var call: Call? = null
        /** Wall-clock time of the data the player last started reading from. */
        @Volatile var openWall = System.currentTimeMillis()
    }

    private val client = XtreamClient.http.newBuilder().readTimeout(20, TimeUnit.SECONDS).build()

    @Volatile private var session: Session? = null

    /** User agent for the recording download (the subscription being watched). */
    @Volatile var userAgent: String = UserAgents.current

    /** Where the next open should start: a wall-clock time, or -1 for the live edge. */
    @Volatile var openTarget = -1L

    init {
        runCatching { root.deleteRecursively() }
    }

    private fun budget(): Long {
        root.mkdirs()
        return minOf(MAX_BYTES, root.usableSpace - RESERVE_BYTES)
    }

    /** False when the device is too short of storage to keep a useful buffer. */
    fun canBuffer(): Boolean = session != null || budget() >= MIN_BYTES

    fun openWall(): Long = session?.openWall ?: System.currentTimeMillis()

    fun oldestWall(): Long {
        val s = session ?: return System.currentTimeMillis()
        synchronized(s.lock) { return s.chunks.firstOrNull()?.startedAt ?: System.currentTimeMillis() }
    }

    @Synchronized
    fun ensureStarted(url: String): Session {
        val current = session
        if (current != null && !current.stopped && current.failure == null && current.url == url) return current
        stop()
        val dir = File(root, "s" + System.nanoTime())
        dir.mkdirs()
        val s = Session(url, dir, budget().coerceAtLeast(MIN_BYTES))
        session = s
        val agent = userAgent
        Thread({ download(s, agent) }, "shedtv-timeshift").apply { isDaemon = true }.start()
        return s
    }

    @Synchronized
    fun stop() {
        val s = session ?: return
        session = null
        s.stopped = true
        runCatching { s.call?.cancel() }
        synchronized(s.lock) { s.lock.notifyAll() }
        runCatching { s.dir.deleteRecursively() }
    }

    /** Index of the chunk a new reader should start at, honouring [openTarget]. */
    fun startIndex(s: Session): Long {
        val target = openTarget
        synchronized(s.lock) {
            if (s.chunks.isEmpty()) {
                s.openWall = System.currentTimeMillis()
                return s.nextIndex
            }
            val chosen = if (target < 0) s.chunks.last()
            else s.chunks.lastOrNull { it.startedAt <= target } ?: s.chunks.first()
            s.openWall = chosen.startedAt
            return chosen.index
        }
    }

    private fun download(s: Session, userAgent: String) {
        var out: FileOutputStream? = null
        try {
            val call = client.newCall(Request.Builder().url(s.url).header("User-Agent", userAgent).build())
            s.call = call
            call.execute().use { response ->
                if (!response.isSuccessful) throw IOException("Provider returned HTTP ${response.code}")
                val input = response.body?.byteStream() ?: throw IOException("Empty stream")
                val buf = ByteArray(188 * 348)
                var current: Chunk? = null
                while (!s.stopped) {
                    val n = input.read(buf)
                    if (n < 0) throw IOException("Stream ended")
                    if (n == 0) continue
                    val now = System.currentTimeMillis()
                    var chunk = current
                    if (chunk == null || now - chunk.startedAt >= CHUNK_MS) {
                        out?.close()
                        val previous = chunk
                        val created = synchronized(s.lock) {
                            previous?.complete = true
                            val fresh = Chunk(s.nextIndex, File(s.dir, "c" + s.nextIndex + ".ts"), now)
                            s.nextIndex++
                            s.chunks.add(fresh)
                            // Drop the oldest chunks once over the time or storage budget.
                            while (s.chunks.size > 2 && (s.totalBytes > s.maxBytes || s.chunks[0].startedAt < now - MAX_MS)) {
                                val old = s.chunks.removeAt(0)
                                s.totalBytes -= old.size
                                old.file.delete()
                            }
                            fresh
                        }
                        out = FileOutputStream(created.file)
                        chunk = created
                        current = created
                    }
                    val writing: Chunk = chunk ?: continue
                    out!!.write(buf, 0, n)
                    synchronized(s.lock) {
                        writing.size += n
                        s.totalBytes += n
                        s.lock.notifyAll()
                    }
                }
            }
        } catch (e: Exception) {
            if (!s.stopped) s.failure = (e as? IOException) ?: IOException(e)
        } finally {
            runCatching { out?.close() }
            synchronized(s.lock) { s.lock.notifyAll() }
        }
    }

    companion object {
        private const val CHUNK_MS = 4_000L
        private const val MAX_MS = 30 * 60 * 1000L
        private const val MAX_BYTES = 1_500L * 1024 * 1024
        private const val MIN_BYTES = 150L * 1024 * 1024
        private const val RESERVE_BYTES = 400L * 1024 * 1024
    }
}

/** Feeds the player from a [TimeshiftBuffer], waiting at the live edge for more data. */
@androidx.annotation.OptIn(UnstableApi::class)
class TimeshiftDataSource(private val timeshift: TimeshiftBuffer) : BaseDataSource(true) {
    private var session: TimeshiftBuffer.Session? = null
    private var index = 0L
    private var position = 0L
    private var file: RandomAccessFile? = null
    private var uri: Uri? = null
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        transferInitializing(dataSpec)
        val s = timeshift.ensureStarted(dataSpec.uri.toString())
        session = s
        index = timeshift.startIndex(s)
        position = 0
        opened = true
        transferStarted(dataSpec)
        return C.LENGTH_UNSET.toLong()
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        val s = session ?: throw IOException("Not open")
        while (true) {
            var chunk: TimeshiftBuffer.Chunk? = null
            var available = 0L
            var complete = false
            synchronized(s.lock) {
                val first = s.chunks.firstOrNull()
                if (first != null && first.index > index) {
                    // Our chunk was trimmed away while paused: skip to the oldest that's left.
                    index = first.index
                    position = 0
                    closeFile()
                }
                if (first != null) chunk = s.chunks.getOrNull((index - first.index).toInt())
                val c = chunk
                if (c != null) {
                    available = c.size - position
                    complete = c.complete
                }
                if (available <= 0 && !complete) {
                    if (s.stopped) throw IOException("Stream stopped")
                    s.failure?.let { throw it }
                    try {
                        s.lock.wait(500)
                    } catch (e: InterruptedException) {
                        throw InterruptedIOException()
                    }
                }
            }
            val c = chunk
            if (c != null && available > 0) {
                val f = file ?: RandomAccessFile(c.file, "r").also { it.seek(position); file = it }
                val n = f.read(buffer, offset, minOf(length.toLong(), available).toInt())
                if (n > 0) {
                    position += n
                    bytesTransferred(n)
                    return n
                }
            } else if (c != null && complete) {
                closeFile()
                index++
                position = 0
            }
        }
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        closeFile()
        session = null
        if (opened) {
            opened = false
            transferEnded()
        }
    }

    private fun closeFile() {
        runCatching { file?.close() }
        file = null
    }
}
