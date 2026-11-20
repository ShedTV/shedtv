@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.matchday.tv.ui

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Text
import com.matchday.tv.AppViewModel
import com.matchday.tv.Screen
import com.matchday.tv.record.RecorderManager
import com.matchday.tv.record.RecordingDataSource
import com.matchday.tv.record.RecordingInfo
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val recDate = SimpleDateFormat("EEE d MMM, HH:mm", Locale.UK)

private fun gb(bytes: Long) = if (bytes >= 1024L * 1024 * 1024) "%.1f GB".format(bytes / 1073741824.0) else "%d MB".format(bytes / 1048576)

private fun mins(ms: Long) = (ms / 60_000).let { if (it >= 60) "${it / 60}h ${it % 60}m" else "${it}m" }

fun recordingSummary(r: RecordingInfo, now: Long): String {
    val end = if (r.active) now else r.finishedAt.takeIf { it > 0 } ?: r.startedAt
    val parts = mutableListOf(mins(end - r.startedAt - r.gapMs), gb(r.bytes))
    if (r.gaps.isNotEmpty()) parts += "${r.gaps.size} gap" + (if (r.gaps.size == 1) "" else "s") + " (${r.gapMs / 1000}s missing)"
    if (r.sourcesUsed.size > 1) parts += "used " + r.sourcesUsed.joinToString(" + ")
    return parts.joinToString("  ·  ")
}

@Composable
fun RecordingsScreen(vm: AppViewModel) {
    val now = rememberNow()
    val first = remember { FocusRequester() }
    var confirm by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { RecorderManager.reload(); delay(120); runCatching { first.requestFocus() } }
    BackHandler { vm.goHome() }
    val list = RecorderManager.recordings

    Row(Modifier.fillMaxSize().background(Palette.Background)) {
        NavRail(vm, Screen.Recordings)
        Column(Modifier.fillMaxSize().padding(start = 28.dp, end = 32.dp, top = 24.dp)) {
            Text("RECORDINGS", color = Palette.Text, fontSize = 26.sp, fontWeight = FontWeight.Bold, fontFamily = DisplayFamily, letterSpacing = 1.sp)
            Text(vm.recordingStorageLine() + "  ·  OK to watch  ·  hold OK to stop or delete", color = Palette.TextMuted, fontSize = 13.sp)
            Spacer(Modifier.padding(top = 10.dp))
            if (list.isEmpty()) {
                Text("Nothing recorded yet. Hold OK (or press ☰) while watching live TV, or on a programme in the guide, to record.",
                    color = Palette.TextMuted, fontSize = 15.sp, modifier = Modifier.padding(top = 30.dp))
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(top = 6.dp, bottom = 40.dp, start = 4.dp, end = 4.dp)) {
                itemsIndexed(list, key = { _, r -> r.id }) { i, r ->
                    FocusTile(
                        onClick = { if (r.parts.isNotEmpty()) vm.playRecording(r.id, tail = false) },
                        onLongClick = {
                            if (r.active) RecorderManager.stop(r.id)
                            else if (confirm == r.id) { RecorderManager.delete(r.id); confirm = null } else confirm = r.id
                        },
                        corner = 12.dp, focusedScale = 1.02f,
                        modifier = Modifier.fillMaxWidth().then(if (i == 0) Modifier.focusRequester(first) else Modifier),
                    ) {
                        Row(Modifier.fillMaxWidth().background(Palette.Card.copy(alpha = 0.5f)).padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(r.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${r.channel}  ·  ${recDate.format(Date(r.startedAt))}", fontSize = 12.sp, maxLines = 1,
                                    color = LocalContentColor.current.copy(alpha = 0.7f))
                                Text(recordingSummary(r, now), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    color = LocalContentColor.current.copy(alpha = 0.7f))
                                if (confirm == r.id) Text("Hold OK again to delete", color = Palette.Live, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                else if (r.active || r.status == "failed") r.notes.lastOrNull()?.let {
                                    Text(it, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = LocalContentColor.current.copy(alpha = 0.6f))
                                }
                            }
                            val (label, colour) = when (r.status) {
                                "recording" -> "● REC until ${formatTime(r.endAt)}" to Palette.Live
                                "complete" -> (if (r.gaps.isEmpty()) "Complete" else "Complete · gaps") to Palette.Accent
                                "stopped" -> "Stopped early" to Palette.TextSoft
                                else -> "Failed" to Palette.Live
                            }
                            Text(label, color = colour, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

/** Plays a recording, part by part; follows it live if it's still recording. */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun RecordingPlayerScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val id = vm.playingRecordingId ?: run { vm.navigate(Screen.Recordings); return }
    val rec = RecorderManager.recordings.firstOrNull { it.id == id } ?: run { vm.navigate(Screen.Recordings); return }
    val focus = remember { FocusRequester() }
    var showOverlay by remember { mutableStateOf(true) }
    var nonce by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(true) }
    var position by remember { mutableLongStateOf(0L) }
    var menuOpen by remember { mutableStateOf(false) }

    val player = remember {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(androidx.media3.datasource.DataSource.Factory { RecordingDataSource() }))
            .build().apply { playWhenReady = true }
    }
    fun uri(i: Int, tail: Boolean) = "shedrec://$id/$i" + if (tail) "?tail=1" else ""

    LaunchedEffect(id) {
        vm.applySubtitlePrefs(player)
        val tail = vm.playingRecordingTail && rec.parts.isNotEmpty()
        val startIndex = if (tail) rec.parts.lastIndex else 0
        player.setMediaItems((startIndex until rec.parts.size).map { MediaItem.fromUri(uri(it, tail && it == startIndex)) })
        player.prepare()
    }
    // Add new parts as a still-running recording reconnects.
    var added by remember { mutableIntStateOf(-1) }
    LaunchedEffect(id) {
        val startIndex = if (vm.playingRecordingTail) rec.parts.lastIndex.coerceAtLeast(0) else 0
        added = rec.parts.size
        while (true) {
            delay(1_000)
            val r = RecorderManager.recordings.firstOrNull { it.id == id } ?: break
            while (added < r.parts.size) { player.addMediaItem(MediaItem.fromUri(uri(added, false))); added++ }
            position = player.currentPosition
            if (!r.active && startIndex >= 0 && player.playbackState == Player.STATE_ENDED) break
        }
    }
    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onPlaybackStateChanged(state: Int) { if (state == Player.STATE_ENDED && RecorderManager.get(id)?.active != true) vm.navigate(Screen.Recordings) }
        }
        player.addListener(l)
        onDispose { player.removeListener(l); player.release() }
    }
    LaunchedEffect(showOverlay, nonce, playing) { if (showOverlay && playing) { delay(4_000); showOverlay = false } }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    BackHandler {
        when {
            menuOpen -> { menuOpen = false; runCatching { focus.requestFocus() } }
            showOverlay && playing -> showOverlay = false
            else -> vm.navigate(Screen.Recordings)
        }
    }
    fun poke() { showOverlay = true; nonce++ }
    fun seekBy(ms: Long) {
        if (player.isCurrentMediaItemSeekable) {
            val d = player.duration.takeIf { it > 0 && it != C.TIME_UNSET } ?: Long.MAX_VALUE
            player.seekTo((player.currentPosition + ms).coerceIn(0, d - 1_000))
        }
        poke()
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown || menuOpen) return@onPreviewKeyEvent false
                when (e.key.nativeKeyCode) {
                    AndroidKeyEvent.KEYCODE_DPAD_CENTER, AndroidKeyEvent.KEYCODE_ENTER, AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                        if (player.isPlaying) player.pause() else player.play(); poke(); true
                    }
                    AndroidKeyEvent.KEYCODE_DPAD_LEFT -> { seekBy(-10_000); true }
                    AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> { seekBy(30_000); true }
                    AndroidKeyEvent.KEYCODE_MEDIA_REWIND -> { seekBy(-60_000); true }
                    AndroidKeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { seekBy(60_000); true }
                    AndroidKeyEvent.KEYCODE_MENU, AndroidKeyEvent.KEYCODE_DPAD_DOWN -> { showOverlay = false; menuOpen = true; true }
                    AndroidKeyEvent.KEYCODE_DPAD_UP, AndroidKeyEvent.KEYCODE_INFO -> { poke(); true }
                    else -> false
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { if (!showOverlay) poke() else { if (player.isPlaying) player.pause() else player.play(); poke() } },
                    onDoubleTap = { o -> if (o.x < size.width / 2) seekBy(-10_000) else seekBy(30_000) },
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
            enter = androidx.compose.animation.slideInHorizontally { it } + fadeIn(),
            exit = androidx.compose.animation.slideOutHorizontally { it } + fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            TrackMenu(vm, player, onLastChannel = null) { menuOpen = false; runCatching { focus.requestFocus() } }
        }
        AnimatedVisibility(visible = (showOverlay || !playing) && !menuOpen, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xD9000000), Color(0xF0000000))))
                        .padding(start = 40.dp, end = 40.dp, top = 80.dp, bottom = 30.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (rec.active) Text("● REC", color = Palette.Live, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(rec.title, color = Palette.Text, fontSize = 30.sp, fontWeight = FontWeight.Bold, fontFamily = DisplayFamily, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text("${rec.channel}  ·  ${recordingSummary(rec, System.currentTimeMillis())}", color = Palette.TextSoft, fontSize = 14.sp, maxLines = 1)
                    Text(
                        (if (playing) "▶" else "❚❚") + "   OK pause  ·  ◀ −10s  ▶ +30s  ·  ◀◀ ▶▶ 1 min  ·  ▼ subtitles & audio" +
                            if (rec.active) "  ·  seeking works once recording ends" else "",
                        color = Palette.TextMuted, fontSize = 12.sp,
                    )
                }
            }
        }
    }
}
