@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.matchday.tv.ui

import android.app.Activity
import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn as AndroidXOptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.matchday.tv.AppViewModel
import com.matchday.tv.data.nowAndNext
import com.matchday.tv.player.FrameRateMatcher
import kotlinx.coroutines.delay

private const val OVERLAY_TIMEOUT_MS = 5_000L
private const val MAX_RETRIES = 5

@AndroidXOptIn(UnstableApi::class)
@Composable
fun PlayerScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val activity = context as Activity
    val channel = vm.currentChannel ?: run { vm.closePlayer(); return }

    var showOverlay by remember { mutableStateOf(true) }
    var overlayNonce by remember { mutableIntStateOf(0) }
    var videoInfo by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>("Connecting…") }
    var retries by remember { mutableIntStateOf(0) }
    var retryNonce by remember { mutableIntStateOf(0) }
    val focus = remember { FocusRequester() }

    val player = remember {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(com.matchday.tv.data.UserAgents.current)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(10_000)
            .setReadTimeoutMs(15_000)
        val loadControl = DefaultLoadControl.Builder()
            // Start quickly, but hold a healthy buffer once running.
            .setBufferDurationsMs(15_000, 50_000, 1_500, 3_000)
            .build()
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(http))
            .setLoadControl(loadControl)
            .build()
            .apply {
                playWhenReady = true
                videoChangeFrameRateStrategy = C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF
            }
    }

    fun poke() {
        showOverlay = true
        overlayNonce++
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_BUFFERING -> if (status == null) status = "Buffering…"
                    Player.STATE_READY -> {
                        status = null
                        retries = 0
                    }
                    Player.STATE_ENDED -> retryNonce++ // live streams shouldn't end; reconnect
                    else -> Unit
                }
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                val fmt = player.videoFormat
                val fps = fmt?.frameRate ?: -1f
                FrameRateMatcher.match(activity, fps)
                val fpsLabel = if (fps > 1f) "${fps.toInt()}fps" else "50Hz"
                videoInfo = if (videoSize.height > 0) "${videoSize.height}p · $fpsLabel" else ""
            }

            override fun onPlayerError(error: PlaybackException) {
                if (retries < MAX_RETRIES) {
                    retries++
                    status = "Reconnecting… ($retries/$MAX_RETRIES)"
                    retryNonce++
                } else {
                    status = "This channel isn't playing. Try another, or switch Format on the channel list."
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
            FrameRateMatcher.reset(activity)
        }
    }

    // (Re)start playback whenever the channel changes or a retry is requested.
    LaunchedEffect(channel.streamId, retryNonce) {
        if (retryNonce > 0 && retries > 0) delay(1_500L * retries)
        val url = vm.streamUrl(channel) ?: return@LaunchedEffect
        val item = MediaItem.Builder()
            .setUri(url)
            .apply { if (url.endsWith(".m3u8")) setMimeType(MimeTypes.APPLICATION_M3U8) }
            .build()
        if (retryNonce == 0 || retries == 0) status = "Connecting…"
        player.setMediaItem(item)
        player.prepare()
        player.play()
    }

    LaunchedEffect(channel.streamId) {
        retries = 0
        vm.loadEpg(channel)
        poke()
    }

    LaunchedEffect(showOverlay, overlayNonce) {
        if (showOverlay) {
            delay(OVERLAY_TIMEOUT_MS)
            showOverlay = false
        }
    }

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    BackHandler {
        if (showOverlay) showOverlay = false else vm.closePlayer()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key.nativeKeyCode) {
                    AndroidKeyEvent.KEYCODE_DPAD_UP, AndroidKeyEvent.KEYCODE_CHANNEL_UP -> { vm.zap(-1); true }
                    AndroidKeyEvent.KEYCODE_DPAD_DOWN, AndroidKeyEvent.KEYCODE_CHANNEL_DOWN -> { vm.zap(1); true }
                    AndroidKeyEvent.KEYCODE_DPAD_CENTER, AndroidKeyEvent.KEYCODE_ENTER,
                    AndroidKeyEvent.KEYCODE_DPAD_LEFT, AndroidKeyEvent.KEYCODE_DPAD_RIGHT,
                    AndroidKeyEvent.KEYCODE_INFO -> {
                        if (showOverlay && event.key.nativeKeyCode == AndroidKeyEvent.KEYCODE_DPAD_CENTER) showOverlay = false else poke()
                        true
                    }
                    AndroidKeyEvent.KEYCODE_MEDIA_REWIND, AndroidKeyEvent.KEYCODE_LAST_CHANNEL -> { vm.lastChannel(); true }
                    else -> false
                }
            }
            .focusRequester(focus)
            .focusable(),
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    keepScreenOn = true
                    this.player = player
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        status?.let {
            Text(
                it, color = Palette.Text, fontSize = 18.sp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color(0xCC07090C), RoundedCornerShape(12.dp))
                    .padding(horizontal = 22.dp, vertical = 12.dp),
            )
        }

        AnimatedVisibility(visible = showOverlay, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            Overlay(vm = vm, videoInfo = videoInfo)
        }
    }
}

@Composable
private fun Overlay(vm: AppViewModel, videoInfo: String) {
    val channel = vm.currentChannel ?: return
    val now = rememberNow()
    val (current, next) = vm.programmesFor(channel).nowAndNext(now)

    Box(Modifier.fillMaxSize()) {
        // Top: tech info and clock
        Row(
            Modifier.align(Alignment.TopEnd).padding(top = 24.dp, end = 36.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (videoInfo.isNotEmpty()) Pill(videoInfo)
            Pill(formatTime(now))
        }

        // Bottom: now playing
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xD9000000), Color(0xF0000000))))
                .padding(start = 36.dp, end = 36.dp, top = 80.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ChannelLogo(channel, 52)
                Column(Modifier.weight(1f)) {
                    Text("${channel.num}  ·  ${channel.name}", color = Palette.TextSoft, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        current?.title ?: channel.name,
                        color = Palette.Text, fontSize = 28.sp, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                if (next != null) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text("NEXT", color = Palette.TextMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
                        Text(
                            "${formatTime(next.start)}  ${next.title}", color = Palette.Text, fontSize = 16.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(320.dp),
                        )
                    }
                }
            }
            if (current != null) {
                ProgressBar(current.progress(now), Modifier.fillMaxWidth(), height = 5.dp, track = Color(0x33FFFFFF))
                Row(Modifier.fillMaxWidth()) {
                    Text(formatTime(current.start), color = Palette.TextSoft, fontSize = 13.sp)
                    Spacer(Modifier.weight(1f))
                    Text(formatTime(current.end), color = Palette.TextSoft, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                "▲▼ change channel  ·  OK hide  ·  ◀◀ last channel  ·  Back to go back",
                color = Palette.TextMuted, fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun Pill(text: String) {
    Text(
        text, color = Palette.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .background(Color(0xCC07090C), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}
