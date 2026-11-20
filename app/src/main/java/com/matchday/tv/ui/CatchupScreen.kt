@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.matchday.tv.ui

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import android.app.Activity
import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.matchday.tv.AppViewModel
import com.matchday.tv.player.FrameRateMatcher
import kotlinx.coroutines.delay

/** Which of the two catch-up address styles last worked, per subscription. */
private val workingStyle = HashMap<String?, Int>()

private const val MIN = 60_000L

/**
 * Watches a programme from the provider's archive. Keeps going through glitches (reconnects where
 * you were, tries the other address style, other versions, then backup subscriptions) and joins
 * the live channel when you catch up.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun CatchupScreen(vm: AppViewModel) {
    val req = vm.catchup ?: run { vm.navigate(com.matchday.tv.Screen.Guide); return }
    val context = LocalContext.current
    val activity = context as Activity
    val sources = remember(req) { vm.catchupSources(req.channel).ifEmpty { listOf(req.channel) } }
    var srcIndex by remember { mutableIntStateOf(0) }
    var style by remember { mutableIntStateOf(workingStyle[sources[0].subId] ?: 0) }
    var fails by remember { mutableIntStateOf(0) }
    var urlStart by remember { mutableLongStateOf(req.from) }
    var pending by remember { mutableStateOf<Long?>(null) }
    var status by remember { mutableStateOf<String?>("Loading catch-up…") }
    var finalError by remember { mutableStateOf(false) }
    var showOverlay by remember { mutableStateOf(true) }
    var nonce by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(true) }
    var menuOpen by remember { mutableStateOf(false) }
    var tick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var bufferingSecs by remember { mutableIntStateOf(0) }
    var okDown by remember { mutableStateOf(false) }
    var swallowOk by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val agent = remember { arrayOf(vm.agentFor(sources[0].subId)) }
    var retryAt by remember { mutableLongStateOf(0L) }
    var retryWall by remember { mutableLongStateOf(0L) }

    val player = remember {
        val factory = DataSource.Factory {
            DefaultHttpDataSource.Factory().setUserAgent(agent[0]).setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(12_000).setReadTimeoutMs(15_000).createDataSource()
        }
        ExoPlayer.Builder(context).setMediaSourceFactory(DefaultMediaSourceFactory(factory)).build().apply {
            playWhenReady = true
            videoChangeFrameRateStrategy = C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF
        }
    }

    fun wall(): Long = urlStart + player.currentPosition.coerceAtLeast(0)
    fun shown(): Long = pending ?: wall()
    val liveProgramme = req.progEnd > System.currentTimeMillis()
    fun maxWall() = minOf(System.currentTimeMillis() - 15_000, req.progEnd + 15 * MIN)

    fun load(at: Long) {
        val src = sources[srcIndex]
        val start = (at / MIN) * MIN
        val end = maxOf(req.progEnd + 15 * MIN, start + 10 * MIN)
        val url = vm.catchupUrl(src, start, ((end - start) / MIN).toInt() + 1, style) ?: return
        agent[0] = vm.agentFor(src.subId)
        urlStart = start
        bufferingSecs = 0
        player.setMediaItem(MediaItem.fromUri(url), (at - start).coerceAtLeast(0))
        player.prepare()
    }

    fun goLive() {
        player.stop()
        vm.joinLive(req.channel, req.returnTo)
    }

    /** Something went wrong: retry here, then the other address style, then the next source. */
    fun failed(reason: String) {
        if (finalError) return
        val at = wall().coerceAtLeast(req.from)
        retryWall = at
        fails++
        when {
            fails <= 2 -> status = "Reconnecting…"
            style == 0 -> { style = 1; fails = 0; status = "Reconnecting…" }
            srcIndex < sources.lastIndex -> {
                srcIndex++; style = workingStyle[sources[srcIndex].subId] ?: 0; fails = 0
                status = "Trying ${vm.subscriptionName(sources[srcIndex].subId)}…"
            }
            else -> {
                finalError = true
                status = "Catch-up isn't available for this programme ($reason). OK to try again, Back to leave."
                player.stop()
                return
            }
        }
        pending = null
        player.stop()
        // Load again shortly (not at once, so a flapping connection isn't hammered).
        retryWall = at
        retryAt = System.currentTimeMillis() + if (fails <= 1) 1_000 else 3_000
    }

    fun seekTo(target: Long) {
        if (finalError) return
        val now = System.currentTimeMillis()
        if (liveProgramme && target >= now - 20_000) { goLive(); return }
        val t = target.coerceIn(req.progStart - 5 * MIN, maxWall())
        val rel = t - urlStart
        val d = player.duration
        if (pending == null && player.isCurrentMediaItemSeekable && rel >= 0 && d != C.TIME_UNSET && rel < d) {
            player.seekTo(rel)
        } else {
            pending = t // reloaded from there once you stop pressing
        }
        showOverlay = true; nonce++
    }

    LaunchedEffect(req) { vm.applySubtitlePrefs(player); load(req.from) }
    LaunchedEffect(pending) {
        val t = pending ?: return@LaunchedEffect
        delay(700)
        load(t)
        pending = null
    }
    // Clock tick: retries, stall watchdog and catching up with live.
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            tick = System.currentTimeMillis()
            if (retryAt in 1..tick) { retryAt = 0; load(retryWall) }
            if (player.playbackState == Player.STATE_BUFFERING && player.playWhenReady && pending == null && !finalError) {
                bufferingSecs++
                if (bufferingSecs >= 15) failed("stalled")
            } else bufferingSecs = 0
            if (liveProgramme && player.isPlaying && wall() >= tick - 20_000) goLive()
        }
    }
    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_READY -> {
                        status = null; fails = 0
                        workingStyle[sources[srcIndex].subId] = style
                    }
                    Player.STATE_ENDED -> {
                        val w = wall()
                        val now = System.currentTimeMillis()
                        when {
                            req.progEnd > now - MIN && w >= now - 3 * MIN -> goLive()
                            w < minOf(req.progEnd, now - MIN) -> failed("stream ended early")
                            else -> { vm.flash = "End of ${req.title}"; vm.navigate(req.returnTo) }
                        }
                    }
                    else -> Unit
                }
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onPlayerError(error: PlaybackException) { failed(error.errorCodeName.removePrefix("ERROR_CODE_").lowercase().replace('_', ' ')) }
            override fun onRenderedFirstFrame() { FrameRateMatcher.match(activity, player.videoFormat?.frameRate ?: -1f, fallback = null) }
        }
        player.addListener(l)
        onDispose {
            player.removeListener(l)
            player.release()
            if (vm.stay50Hz) FrameRateMatcher.match(activity, 50f) else FrameRateMatcher.reset(activity)
        }
    }
    // Home or sleep: let go of the connection, then carry on from the same moment.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var resumeAt by remember { mutableLongStateOf(0L) }
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> { resumeAt = wall(); player.stop() }
                androidx.lifecycle.Lifecycle.Event.ON_START -> if (resumeAt > 0) { load(resumeAt); resumeAt = 0 }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(showOverlay, nonce, playing) { if (showOverlay && playing) { delay(4_500); showOverlay = false } }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    BackHandler {
        when {
            menuOpen -> { menuOpen = false; runCatching { focus.requestFocus() } }
            showOverlay && playing && !finalError -> showOverlay = false
            else -> { player.stop(); vm.navigate(req.returnTo) }
        }
    }
    fun poke() { showOverlay = true; nonce++ }
    fun retryAll() { finalError = false; srcIndex = 0; style = 0; fails = 0; status = "Loading catch-up…"; load(if (retryWall > 0) retryWall else req.from) }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .onPreviewKeyEvent { e ->
                val code = e.key.nativeKeyCode
                val isOk = code == AndroidKeyEvent.KEYCODE_DPAD_CENTER || code == AndroidKeyEvent.KEYCODE_ENTER
                if (isOk && swallowOk) { if (e.type == KeyEventType.KeyUp) swallowOk = false; return@onPreviewKeyEvent true }
                if (menuOpen) return@onPreviewKeyEvent false
                if (isOk) {
                    if (e.type == KeyEventType.KeyDown) {
                        if (e.nativeKeyEvent.repeatCount == 0) okDown = true
                        else if (okDown) { okDown = false; swallowOk = true; showOverlay = false; menuOpen = true }
                    } else if (e.type == KeyEventType.KeyUp && okDown) {
                        okDown = false
                        if (finalError) retryAll() else { if (player.isPlaying) player.pause() else player.play(); poke() }
                    }
                    return@onPreviewKeyEvent true
                }
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (code) {
                    AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, AndroidKeyEvent.KEYCODE_MEDIA_PLAY, AndroidKeyEvent.KEYCODE_MEDIA_PAUSE -> {
                        if (player.isPlaying) player.pause() else player.play(); poke(); true
                    }
                    AndroidKeyEvent.KEYCODE_DPAD_LEFT -> { seekTo(shown() - 30_000); true }
                    AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> { seekTo(shown() + 30_000); true }
                    AndroidKeyEvent.KEYCODE_MEDIA_REWIND -> { seekTo(shown() - 5 * MIN); true }
                    AndroidKeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { seekTo(shown() + 5 * MIN); true }
                    AndroidKeyEvent.KEYCODE_MENU, AndroidKeyEvent.KEYCODE_DPAD_DOWN -> { showOverlay = false; menuOpen = true; true }
                    AndroidKeyEvent.KEYCODE_DPAD_UP, AndroidKeyEvent.KEYCODE_INFO -> { poke(); true }
                    else -> false
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { if (!showOverlay) poke() else { if (player.isPlaying) player.pause() else player.play(); poke() } },
                    onDoubleTap = { o -> if (o.x < size.width / 2) seekTo(shown() - 30_000) else seekTo(shown() + 30_000) },
                    onLongPress = { showOverlay = false; menuOpen = true },
                )
            }
            .focusRequester(focus).focusable(),
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    keepScreenOn = true
                    styleSubtitles()
                    this.player = player
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        AnimatedVisibility(
            visible = menuOpen,
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            val extras = buildList {
                if (liveProgramme) add(MenuAction("●  Go live") { goLive() })
                add(MenuAction("⟲  Back to the start") { seekTo(req.from) })
            }
            TrackMenu(vm, player, onLastChannel = null, extraActions = extras) { menuOpen = false; runCatching { focus.requestFocus() } }
        }
        val now = tick
        val pos = shown()
        AnimatedVisibility(visible = (showOverlay || !playing || status != null) && !menuOpen, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                status?.let {
                    Text(it, color = Palette.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 30.dp)
                            .background(Color(0xD907090C), RoundedCornerShape(10.dp)).padding(horizontal = 18.dp, vertical = 10.dp))
                }
                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xD9000000), Color(0xF0000000))))
                        .padding(start = 40.dp, end = 40.dp, top = 80.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("⟲ CATCH-UP", color = Palette.Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(req.title, color = Palette.Text, fontSize = 28.sp, fontWeight = FontWeight.Bold, fontFamily = DisplayFamily, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    val src = sources[srcIndex]
                    Text(
                        vm.channelLabel(req.channel) + "  ·  " + formatTime(req.progStart) + "–" + formatTime(req.progEnd) +
                            (if (src.subId != null || srcIndex > 0) "  ·  via ${vm.subscriptionName(src.subId)}" else ""),
                        color = Palette.TextSoft, fontSize = 14.sp, maxLines = 1,
                    )
                    val span = (req.progEnd - req.progStart).coerceAtLeast(1)
                    ProgressBar(((pos - req.progStart).toFloat() / span).coerceIn(0f, 1f), Modifier.fillMaxWidth(), height = 5.dp)
                    Row(Modifier.fillMaxWidth()) {
                        Text("Watching ${formatTime(pos)}" + if (pending != null) " ⏩" else "", color = Palette.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        val right = if (liveProgramme) {
                            val behind = ((now - pos) / MIN).coerceAtLeast(0)
                            if (behind >= 60) "${behind / 60}h ${behind % 60}m behind live" else "$behind min behind live"
                        } else "ends ${formatTime(req.progEnd)}"
                        Text(right, color = Palette.TextSoft, fontSize = 14.sp)
                    }
                    Text(
                        (if (playing) "▶" else "❚❚") + "   OK pause  ·  ◀ ▶ 30s  ·  ◀◀ ▶▶ 5 min  ·  ▼ or hold OK for options" +
                            if (liveProgramme) "  ·  skip past now to go live" else "",
                        color = Palette.TextMuted, fontSize = 12.sp,
                    )
                }
            }
        }
    }
}
