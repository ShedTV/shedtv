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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
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
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.matchday.tv.AppViewModel
import com.matchday.tv.data.Playable
import com.matchday.tv.player.FrameRateMatcher
import kotlinx.coroutines.delay

private const val SEEK_SMALL = 10_000L
private const val SEEK_BIG = 60_000L

@AndroidXOptIn(UnstableApi::class)
@Composable
fun VodPlayerScreen(vm: AppViewModel) {
    val lib = vm.library
    val context = LocalContext.current
    val activity = context as Activity
    var item by remember { mutableStateOf(lib.playing) }
    val current = item ?: run { vm.closePlayer(); return }

    var showOverlay by remember { mutableStateOf(true) }
    var overlayNonce by remember { mutableIntStateOf(0) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf<String?>("Loading…") }
    var upNextIn by remember { mutableIntStateOf(-1) }
    val focus = remember { FocusRequester() }

    val player = remember {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(com.matchday.tv.data.UserAgents.current)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(30_000)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(http))
            .build()
            .apply {
                playWhenReady = true
                videoChangeFrameRateStrategy = C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF
            }
    }

    fun poke() { showOverlay = true; overlayNonce++ }
    fun save() {
        val p = item ?: return
        val d = player.duration
        if (d > 0 && d != C.TIME_UNSET) lib.savePosition(p, player.currentPosition, d)
    }
    fun playNext(): Boolean {
        val p = item ?: return false
        val next = p.upNext.firstOrNull() ?: return false
        item = next.copy(upNext = p.upNext.drop(1))
        return true
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_BUFFERING -> if (status == null) status = "Buffering…"
                    Player.STATE_READY -> status = null
                    Player.STATE_ENDED -> {
                        save()
                        if (item?.upNext?.isNotEmpty() == true) upNextIn = 5 else vm.closePlayer()
                    }
                    else -> Unit
                }
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                FrameRateMatcher.match(activity, player.videoFormat?.frameRate ?: -1f, fallback = null)
            }
            override fun onPlayerError(error: PlaybackException) {
                status = "Couldn't play this (${error.errorCodeName}). Try again or pick another."
            }
        }
        player.addListener(listener)
        onDispose {
            save()
            player.removeListener(listener)
            player.release()
            FrameRateMatcher.reset(activity)
        }
    }

    // Load whatever is current; resumes from its saved point.
    LaunchedEffect(current.id) {
        status = "Loading…"
        upNextIn = -1
        player.setMediaItem(MediaItem.fromUri(current.url), current.startAt)
        player.prepare()
        player.play()
        poke()
    }

    // Tick: progress for the overlay, and a resume point every 15 seconds.
    LaunchedEffect(Unit) {
        var n = 0
        while (true) {
            delay(1_000)
            position = player.currentPosition
            duration = player.duration.takeIf { it > 0 && it != C.TIME_UNSET } ?: 0
            if (++n % 15 == 0 && player.isPlaying) save()
        }
    }

    // "Next episode in 5…" countdown.
    LaunchedEffect(upNextIn) {
        if (upNextIn > 0) { delay(1_000); upNextIn-- }
        else if (upNextIn == 0) { if (!playNext()) vm.closePlayer() }
    }

    LaunchedEffect(showOverlay, overlayNonce, playing) {
        if (showOverlay && playing) { delay(4_000); showOverlay = false }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    BackHandler {
        when {
            upNextIn >= 0 -> { upNextIn = -1; vm.closePlayer() }
            showOverlay && playing -> showOverlay = false
            else -> vm.closePlayer()
        }
    }

    fun seekBy(ms: Long) {
        val d = player.duration.takeIf { it > 0 && it != C.TIME_UNSET } ?: Long.MAX_VALUE
        player.seekTo((player.currentPosition + ms).coerceIn(0, d - 1_000))
        position = player.currentPosition
        poke()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key.nativeKeyCode) {
                    AndroidKeyEvent.KEYCODE_DPAD_CENTER, AndroidKeyEvent.KEYCODE_ENTER,
                    AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, AndroidKeyEvent.KEYCODE_SPACE -> {
                        if (upNextIn >= 0) { upNextIn = 0 } else if (player.isPlaying) player.pause() else player.play()
                        poke(); true
                    }
                    AndroidKeyEvent.KEYCODE_MEDIA_PLAY -> { player.play(); poke(); true }
                    AndroidKeyEvent.KEYCODE_MEDIA_PAUSE -> { player.pause(); poke(); true }
                    AndroidKeyEvent.KEYCODE_DPAD_LEFT -> { seekBy(-SEEK_SMALL); true }
                    AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> { seekBy(SEEK_SMALL * 3); true }
                    AndroidKeyEvent.KEYCODE_MEDIA_REWIND -> { seekBy(-SEEK_BIG); true }
                    AndroidKeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { seekBy(SEEK_BIG); true }
                    AndroidKeyEvent.KEYCODE_DPAD_UP, AndroidKeyEvent.KEYCODE_DPAD_DOWN, AndroidKeyEvent.KEYCODE_INFO -> { poke(); true }
                    AndroidKeyEvent.KEYCODE_MEDIA_NEXT -> { save(); playNext(); true }
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
                modifier = Modifier.align(Alignment.Center)
                    .background(Color(0xCC07090C), RoundedCornerShape(12.dp))
                    .padding(horizontal = 22.dp, vertical = 12.dp),
            )
        }

        if (upNextIn >= 0) {
            val next = current.upNext.firstOrNull()
            Column(
                Modifier.align(Alignment.BottomEnd).padding(36.dp)
                    .background(Color(0xE607090C), RoundedCornerShape(14.dp)).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("UP NEXT IN $upNextIn", color = Palette.Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text(next?.subtitle.orEmpty(), color = Palette.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text("OK to play now  ·  Back to stop", color = Palette.TextMuted, fontSize = 12.sp)
            }
        }

        AnimatedVisibility(visible = showOverlay || !playing, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            VodOverlay(current, position, duration, playing)
        }
    }
}

@Composable
private fun VodOverlay(p: Playable, position: Long, duration: Long, playing: Boolean) {
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xD9000000), Color(0xF0000000))))
                .padding(start = 40.dp, end = 40.dp, top = 80.dp, bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(p.title, color = Palette.Text, fontSize = 28.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (p.subtitle.isNotBlank()) Text(p.subtitle, color = Palette.TextSoft, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            ProgressBar(if (duration > 0) position.toFloat() / duration else 0f, Modifier.fillMaxWidth(), height = 6.dp, track = Color(0x33FFFFFF))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (playing) "▶" else "❚❚", color = Palette.Accent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.padding(start = 10.dp))
                Text(clock(position), color = Palette.TextSoft, fontSize = 14.sp)
                Spacer(Modifier.weight(1f))
                Text("◀ −10s   ▶ +30s   ◀◀ ▶▶ 1 min   OK pause", color = Palette.TextMuted, fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                Text(if (duration > 0) "-" + clock(duration - position) else "", color = Palette.TextSoft, fontSize = 14.sp)
            }
        }
    }
}

private fun clock(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}
