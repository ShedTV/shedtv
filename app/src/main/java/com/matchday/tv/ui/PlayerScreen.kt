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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.rememberUpdatedState
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
private const val LIVE_EDGE_MS = 20_000L

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

    // The same channel is often carried on several streams; these are the backups, best first.
    val sources = remember(channel.streamId) { vm.sourcesFor(channel) }
    var sourceChoice by remember { mutableStateOf(channel.streamId to 0) }
    val sourceIndex = if (sourceChoice.first == channel.streamId) sourceChoice.second.coerceIn(0, sources.lastIndex) else 0
    val activeSource = sources[sourceIndex]
    val latestSources by rememberUpdatedState(sources)
    val latestSourceIndex by rememberUpdatedState(sourceIndex)
    val latestChannelId by rememberUpdatedState(channel.streamId)

    // Live rewind: the shared player reads from a recording of the stream rather than the network.
    val live = vm.live
    val timeshift = live.timeshift
    var rewindActive by remember { mutableStateOf(live.isRecording) }
    var behindMs by remember { mutableStateOf(0L) }
    var paused by remember { mutableStateOf(false) }
    var listOpen by remember { mutableStateOf(false) }
    val player = live.player

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
                val backupsLeft = latestSources.size - 1 - latestSourceIndex
                if (retries >= 1 && backupsLeft > 0) {
                    // One reconnect didn't fix it: move to the next backup stream.
                    status = "Stream problem. Switching to source ${latestSourceIndex + 2} of ${latestSources.size}…"
                    retries = 0
                    sourceChoice = latestChannelId to latestSourceIndex + 1
                } else if (retries < MAX_RETRIES) {
                    retries++
                    status = "Reconnecting… ($retries/$MAX_RETRIES)"
                    retryNonce++
                } else {
                    status = "This channel isn't playing. Try another, or change Stream format in Settings."
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            // Hand back to the preview (muted) or let the app close the connection.
            live.setMuted(true)
            if (vm.stay50Hz) FrameRateMatcher.match(activity, 50f) else FrameRateMatcher.reset(activity)
        }
    }

    // (Re)start playback whenever the channel changes or a retry is requested.
    LaunchedEffect(activeSource.uid, retryNonce) {
        if (retryNonce > 0 && retries > 0) delay(1_500L * retries)
        val url = vm.streamUrl(activeSource) ?: return@LaunchedEffect
        paused = false
        behindMs = 0
        val adopting = retryNonce == 0 && live.currentUrl == url && player.playbackState != Player.STATE_IDLE
        if (adopting) {
            // The preview was already playing this: carry on, just with sound. No second connection.
            live.setMuted(false)
            rewindActive = live.isRecording
            if (player.playbackState == Player.STATE_READY) status = null
            player.videoFormat?.let { f ->
                FrameRateMatcher.match(activity, f.frameRate)
                if (player.videoSize.height > 0) videoInfo = "${player.videoSize.height}p · " + (if (f.frameRate > 1f) "${f.frameRate.toInt()}fps" else "50Hz")
            }
        } else {
            if (retries == 0 && sourceIndex == 0) status = "Connecting…"
            rewindActive = live.start(url, activeSource.uid, rewind = vm.liveRewind, muted = false, userAgent = vm.agentFor(activeSource.subId))
        }
    }

    LaunchedEffect(channel.streamId) {
        retries = 0
        vm.loadEpg(channel)
        poke()
    }

    // A stream stuck buffering counts as failed: move to a backup if there is one.
    LaunchedEffect(status, sourceIndex, channel.streamId) {
        if (status == "Buffering…" && sourceIndex < sources.lastIndex) {
            delay(12_000)
            status = "Still buffering. Switching to source ${sourceIndex + 2} of ${sources.size}…"
            retries = 0
            sourceChoice = channel.streamId to sourceIndex + 1
        }
    }

    // Stop the stream when the app goes to the background (Home button), resume on return.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> {
                    live.stop()
                    paused = false
                }
                androidx.lifecycle.Lifecycle.Event.ON_START ->
                    if (live.currentUrl == null) retryNonce++
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Track how far behind live we are; never let a pause outlast the recording.
    LaunchedEffect(rewindActive) {
        while (rewindActive) {
            delay(500)
            val playPoint = timeshift.openWall() + player.currentPosition
            behindMs = (System.currentTimeMillis() - playPoint).coerceAtLeast(0)
            if (paused && playPoint < timeshift.oldestWall() + 15_000) {
                paused = false
                player.play()
            }
        }
    }

    /** Jump to [target] milliseconds behind live (0 = live). */
    fun seekBehind(target: Long) {
        poke()
        if (!rewindActive) return
        val now = System.currentTimeMillis()
        val maxBehind = (now - timeshift.oldestWall()).coerceAtLeast(0)
        if (maxBehind < 5_000) return
        val behind = target.coerceIn(0, maxBehind)
        val url = vm.streamUrl(activeSource) ?: return
        timeshift.openTarget = if (behind < 8_000) -1 else now - behind
        paused = false
        behindMs = behind
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        player.play()
    }

    LaunchedEffect(showOverlay, overlayNonce, paused) {
        if (showOverlay && !paused) {
            delay(OVERLAY_TIMEOUT_MS)
            showOverlay = false
        }
    }

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    BackHandler {
        when {
            listOpen -> { listOpen = false; runCatching { focus.requestFocus() } }
            showOverlay -> showOverlay = false
            else -> vm.closePlayer()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                // While the channel list is open, it gets the remote (Right closes it).
                if (listOpen) {
                    if (event.key.nativeKeyCode == AndroidKeyEvent.KEYCODE_DPAD_RIGHT) {
                        listOpen = false
                        runCatching { focus.requestFocus() }
                        return@onPreviewKeyEvent true
                    }
                    return@onPreviewKeyEvent false
                }
                when (event.key.nativeKeyCode) {
                    AndroidKeyEvent.KEYCODE_DPAD_UP, AndroidKeyEvent.KEYCODE_CHANNEL_UP -> { vm.zap(-1); true }
                    AndroidKeyEvent.KEYCODE_DPAD_DOWN, AndroidKeyEvent.KEYCODE_CHANNEL_DOWN -> { vm.zap(1); true }
                    AndroidKeyEvent.KEYCODE_DPAD_LEFT -> { seekBehind(behindMs + 15_000); true }
                    AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> { if (behindMs > LIVE_EDGE_MS) seekBehind(behindMs - 15_000) else poke(); true }
                    AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, AndroidKeyEvent.KEYCODE_MEDIA_PLAY, AndroidKeyEvent.KEYCODE_MEDIA_PAUSE -> {
                        if (rewindActive) {
                            paused = !paused
                            if (paused) player.pause() else player.play()
                        }
                        poke()
                        true
                    }
                    AndroidKeyEvent.KEYCODE_MENU -> { vm.lastChannel(); true }
                    AndroidKeyEvent.KEYCODE_DPAD_CENTER, AndroidKeyEvent.KEYCODE_ENTER -> {
                        showOverlay = false
                        listOpen = true
                        true
                    }
                    AndroidKeyEvent.KEYCODE_INFO -> {
                        if (showOverlay) showOverlay = false else poke()
                        true
                    }
                    AndroidKeyEvent.KEYCODE_LAST_CHANNEL -> { vm.lastChannel(); true }
                    AndroidKeyEvent.KEYCODE_MEDIA_REWIND -> { if (rewindActive) seekBehind(behindMs + 60_000) else vm.lastChannel(); true }
                    AndroidKeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                        if (rewindActive && behindMs > LIVE_EDGE_MS) {
                            seekBehind(behindMs - 60_000)
                        } else if (sources.size > 1) {
                            val next = (sourceIndex + 1) % sources.size
                            retries = 0
                            status = "Switching to source ${next + 1} of ${sources.size}…"
                            sourceChoice = channel.streamId to next
                        }
                        poke()
                        true
                    }
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

        AnimatedVisibility(
            visible = listOpen,
            enter = androidx.compose.animation.slideInHorizontally { -it } + fadeIn(),
            exit = androidx.compose.animation.slideOutHorizontally { -it } + fadeOut(),
        ) {
            ChannelListPanel(vm) { index ->
                vm.playAt(index)
                listOpen = false
                runCatching { focus.requestFocus() }
            }
        }

        AnimatedVisibility(visible = showOverlay && !listOpen, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            Overlay(
                vm = vm, videoInfo = videoInfo,
                sourceLabel = if (sources.size > 1 || activeSource.subId != null) {
                    val q = com.matchday.tv.data.ChannelNames.quality(activeSource.name)
                    val provider = if (activeSource.subId != null) vm.subscriptionName(activeSource.subId) + " · " else ""
                    provider + (if (q.isNotEmpty()) "$q · " else "") + "source ${sourceIndex + 1} of ${sources.size}"
                } else "",
                rewindLabel = when {
                    !rewindActive -> ""
                    paused -> "❚❚  Paused  ·  ${behindClock(behindMs)} behind live"
                    behindMs > LIVE_EDGE_MS -> "◀◀  ${behindClock(behindMs)} behind live  ·  ▶▶ to catch up"
                    else -> "●  LIVE"
                },
                behind = rewindActive && (paused || behindMs > LIVE_EDGE_MS),
            )
        }

        // Always show when you're not watching live, even with the info bar hidden.
        if (rewindActive && !showOverlay && (paused || behindMs > LIVE_EDGE_MS)) {
            Text(
                (if (paused) "❚❚  " else "") + "−" + behindClock(behindMs),
                color = Palette.Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopStart).padding(top = 24.dp, start = 36.dp)
                    .background(Color(0xCC07090C), RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

/** Slim channel list down the left of the picture; the match keeps playing behind it. */
@Composable
private fun ChannelListPanel(vm: AppViewModel, onPick: (Int) -> Unit) {
    val now = rememberNow()
    val list = vm.playlist
    val state = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = (vm.playingIndex - 4).coerceAtLeast(0))
    val current = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(60); runCatching { current.requestFocus() } }
    Column(
        Modifier.fillMaxHeight().width(420.dp)
            .background(Brush.horizontalGradient(listOf(Color(0xF507090C), Color(0xE607090C), Color(0x0007090C))))
            .padding(start = 24.dp, top = 22.dp, end = 40.dp),
    ) {
        val cat = vm.categoryList.firstOrNull { it.id == vm.selectedCategoryId }?.name ?: "Channels"
        Text(cat.uppercase(), color = Palette.Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Text("OK to watch  ·  Back or ▶ to close", color = Palette.TextMuted, fontSize = 11.sp, modifier = Modifier.padding(bottom = 10.dp))
        androidx.compose.foundation.lazy.LazyColumn(
            state = state,
            verticalArrangement = Arrangement.spacedBy(3.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 6.dp, horizontal = 4.dp),
        ) {
            items(list.size, key = { list[it].streamId }) { i ->
                val ch = list[i]
                val playing = i == vm.playingIndex
                LaunchedEffect(ch.streamId) { vm.loadEpg(ch) }
                val (nowProg, _) = vm.programmesFor(ch).nowAndNext(now)
                FocusTile(
                    onClick = { onPick(i) }, selected = playing, corner = 10.dp, focusedScale = 1.02f,
                    modifier = Modifier.fillMaxWidth().then(if (playing) Modifier.focusRequester(current) else Modifier),
                ) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ChannelLogo(ch, 34)
                        Column(Modifier.weight(1f)) {
                            Text(ch.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(nowProg?.title ?: "", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                color = androidx.tv.material3.LocalContentColor.current.copy(alpha = 0.7f))
                            if (nowProg != null) ProgressBar(nowProg.progress(now), Modifier.fillMaxWidth().padding(top = 4.dp), height = 3.dp)
                        }
                    }
                }
            }
        }
    }
}

private fun behindClock(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
private fun Overlay(vm: AppViewModel, videoInfo: String, sourceLabel: String, rewindLabel: String, behind: Boolean) {
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
            if (sourceLabel.isNotEmpty()) Pill(sourceLabel)
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
                        color = Palette.Text, fontSize = 34.sp, fontWeight = FontWeight.Bold, fontFamily = DisplayFamily,
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
            if (rewindLabel.isNotEmpty()) {
                Text(rewindLabel, color = if (behind) Palette.Accent else Palette.Live, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(2.dp))
            Text(
                "OK channel list  ·  ▲▼ channel  ·  " + (if (rewindLabel.isNotEmpty()) "◀ ▶ 15s  ·  ◀◀ ▶▶ 1 min  ·  ⏯ pause  ·  ☰ last channel" else "◀◀ last channel") +
                    (if (sourceLabel.isNotEmpty()) "  ·  ▶▶ at live: switch source" else "") + "  ·  Back hide",
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
