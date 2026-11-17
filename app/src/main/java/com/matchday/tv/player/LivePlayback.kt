package com.matchday.tv.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.matchday.tv.data.UserAgents
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The one live-TV player in the app. The preview in Live TV and the guide, and the full-screen
 * player, all use it, so pressing OK on a previewing channel carries on the same connection
 * rather than opening a second one.
 */
@OptIn(UnstableApi::class)
class LivePlayback(private val context: Context) {
    val timeshift = TimeshiftBuffer(File(context.cacheDir, "timeshift"))
    val useTimeshift = AtomicBoolean(false)

    /** What's playing now, or null when stopped. */
    var currentUrl: String? = null
        private set
    var currentStreamId: String? = null
        private set

    /** Bumped on every stop, so screens know to start their preview again. */
    var stops by mutableIntStateOf(0)
        private set

    private var created = false
    val player: ExoPlayer by lazy {
        created = true
        val factory = DataSource.Factory {
            if (useTimeshift.get()) TimeshiftDataSource(timeshift)
            else DefaultHttpDataSource.Factory()
                .setUserAgent(UserAgents.current)
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(10_000)
                .setReadTimeoutMs(15_000)
                .createDataSource()
        }
        val loadControl = DefaultLoadControl.Builder()
            // Start quickly, but hold a healthy buffer once running.
            .setBufferDurationsMs(15_000, 50_000, 1_500, 3_000)
            .build()
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(factory))
            .setLoadControl(loadControl)
            .build()
            .apply {
                playWhenReady = true
                videoChangeFrameRateStrategy = C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF
            }
    }

    val isRecording: Boolean get() = useTimeshift.get()

    /** Start a stream (closing whatever was playing). Returns true if live rewind is recording it. */
    fun start(url: String, streamId: String, rewind: Boolean, muted: Boolean): Boolean {
        val buffered = rewind && !url.endsWith(".m3u8") && timeshift.canBuffer()
        if (!buffered) timeshift.stop()
        useTimeshift.set(buffered)
        timeshift.openTarget = -1
        currentUrl = url
        currentStreamId = streamId
        player.volume = if (muted) 0f else 1f
        val item = MediaItem.Builder()
            .setUri(url)
            .apply { if (url.endsWith(".m3u8")) setMimeType(MimeTypes.APPLICATION_M3U8) }
            .build()
        player.setMediaItem(item)
        player.prepare()
        player.play()
        return buffered
    }

    fun setMuted(muted: Boolean) {
        if (created) player.volume = if (muted) 0f else 1f
    }

    /** Close the connection. */
    fun stop() {
        if (!created) return
        if (currentUrl == null && player.mediaItemCount == 0) return
        player.stop()
        player.clearMediaItems()
        timeshift.stop()
        timeshift.openTarget = -1
        useTimeshift.set(false)
        currentUrl = null
        currentStreamId = null
        stops++
    }

    fun release() {
        stop()
        if (created) player.release()
    }
}
