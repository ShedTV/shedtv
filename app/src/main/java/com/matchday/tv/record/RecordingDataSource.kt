package com.matchday.tv.record

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import java.io.IOException
import java.io.InterruptedIOException
import java.io.RandomAccessFile

/**
 * Plays one part of a recording: shedrec://<id>/<part>. A part still being written is followed
 * as it grows ("watch while recording"); ?tail=1 starts near the newest video.
 */
@androidx.annotation.OptIn(UnstableApi::class)
class RecordingDataSource : BaseDataSource(false) {
    private var file: RandomAccessFile? = null
    private var uri: Uri? = null
    private var id = ""
    private var index = 0
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        id = dataSpec.uri.host.orEmpty()
        index = dataSpec.uri.lastPathSegment?.toIntOrNull() ?: 0
        val f = RecorderManager.partFile(id, index) ?: throw IOException("Recording not found")
        transferInitializing(dataSpec)
        val raf = RandomAccessFile(f, "r")
        file = raf
        var start = dataSpec.position
        if (dataSpec.position == 0L && dataSpec.uri.getQueryParameter("tail") == "1") {
            start = ((raf.length() - 3L * 1024 * 1024).coerceAtLeast(0) / 188) * 188
        }
        raf.seek(start)
        opened = true
        transferStarted(dataSpec)
        return if (RecorderManager.isGrowing(id, index)) C.LENGTH_UNSET.toLong() else (raf.length() - start).coerceAtLeast(0)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        val f = file ?: throw IOException("Not open")
        while (true) {
            val n = f.read(buffer, offset, length)
            if (n > 0) {
                bytesTransferred(n)
                return n
            }
            if (!RecorderManager.isGrowing(id, index)) return C.RESULT_END_OF_INPUT
            try {
                Thread.sleep(300)
            } catch (e: InterruptedException) {
                throw InterruptedIOException()
            }
        }
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        runCatching { file?.close() }
        file = null
        if (opened) {
            opened = false
            transferEnded()
        }
    }
}
