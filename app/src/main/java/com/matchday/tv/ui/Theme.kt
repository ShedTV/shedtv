@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.matchday.tv.ui

import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import com.matchday.tv.R
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.darkColorScheme
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Palette {
    /** Set from Settings → Display; the UI is rebuilt when these change. */
    @Volatile var highContrast = false
    @Volatile var accentArgb: Long = 0xFF44C470

    val Background get() = if (highContrast) Color(0xFF000000) else Color(0xFF07090C)
    val Card get() = if (highContrast) Color(0xFF1B232C) else Color(0xFF10151B)
    val CardRaised get() = if (highContrast) Color(0xFF26313D) else Color(0xFF16202A)
    val Text get() = if (highContrast) Color(0xFFFFFFFF) else Color(0xFFF2F5F8)
    val TextSoft get() = if (highContrast) Color(0xFFF2F5F8) else Color(0xFFC9D3DE)
    val TextMuted get() = if (highContrast) Color(0xFFD3DCE5) else Color(0xFF8C9AAB)
    val Accent get() = Color(accentArgb)
    val Live = Color(0xFFFF3B3B)
    val Divider get() = if (highContrast) Color(0x66FFFFFF) else Color(0x1FFFFFFF)

    /** Accent colours to choose from (yellow and blue are easiest to tell apart with colour blindness). */
    val accents = listOf(
        "Green" to 0xFF44C470, "Blue" to 0xFF3D9BFF, "Yellow" to 0xFFFFD23F,
        "Orange" to 0xFFFF9F2E, "Pink" to 0xFFFF5FA2, "Purple" to 0xFFA77BFF,
    )
}

/** Body text: Inter. */
val InterFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
    Font(R.font.inter_extrabold, FontWeight.ExtraBold),
)

/** Big text (headings, team names, times): Barlow Condensed, as in the mockups. */
val DisplayFamily = FontFamily(
    Font(R.font.barlow_condensed_semibold, FontWeight.SemiBold),
    Font(R.font.barlow_condensed_bold, FontWeight.Bold),
)

private fun TextStyle.inter() = copy(fontFamily = InterFamily)

private fun androidx.tv.material3.Typography.withInter() = copy(
    displayLarge = displayLarge.inter(), displayMedium = displayMedium.inter(), displaySmall = displaySmall.inter(),
    headlineLarge = headlineLarge.inter(), headlineMedium = headlineMedium.inter(), headlineSmall = headlineSmall.inter(),
    titleLarge = titleLarge.inter(), titleMedium = titleMedium.inter(), titleSmall = titleSmall.inter(),
    bodyLarge = bodyLarge.inter(), bodyMedium = bodyMedium.inter(), bodySmall = bodySmall.inter(),
    labelLarge = labelLarge.inter(), labelMedium = labelMedium.inter(), labelSmall = labelSmall.inter(),
)

private fun androidx.compose.material3.Typography.withInter() = copy(
    displayLarge = displayLarge.inter(), displayMedium = displayMedium.inter(), displaySmall = displaySmall.inter(),
    headlineLarge = headlineLarge.inter(), headlineMedium = headlineMedium.inter(), headlineSmall = headlineSmall.inter(),
    titleLarge = titleLarge.inter(), titleMedium = titleMedium.inter(), titleSmall = titleSmall.inter(),
    bodyLarge = bodyLarge.inter(), bodyMedium = bodyMedium.inter(), bodySmall = bodySmall.inter(),
    labelLarge = labelLarge.inter(), labelMedium = labelMedium.inter(), labelSmall = labelSmall.inter(),
)

@Composable
fun MatchdayTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        typography = androidx.tv.material3.Typography().withInter(),
        colorScheme = darkColorScheme(
            primary = Palette.Accent,
            onPrimary = Palette.Background,
            background = Palette.Background,
            onBackground = Palette.Text,
            surface = Palette.Card,
            onSurface = Palette.Text,
        )
    ) {
        androidx.compose.material3.MaterialTheme(
            typography = androidx.compose.material3.Typography().withInter(),
            colorScheme = androidx.compose.material3.darkColorScheme(
                primary = Palette.Accent,
                onPrimary = Palette.Background,
                background = Palette.Background,
                surface = Palette.Card,
                onSurface = Palette.Text,
            ),
            content = content,
        )
    }
}

/** A focusable tile in the house style: lifts and gains an accent ring when focused. */
@Composable
fun FocusTile(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    selected: Boolean = false,
    corner: Dp = 12.dp,
    focusedScale: Float = 1.03f,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(corner)
    val touchFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        // TV surfaces only answer the remote: taps and long presses on touch screens come in here.
        modifier = modifier.focusRequester(touchFocus).touchTap(
            onTap = { runCatching { touchFocus.requestFocus() }; onClick() },
            onLongPress = onLongClick?.let { l -> { runCatching { touchFocus.requestFocus() }; l() } },
        ),
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = if (selected) Palette.Accent else Palette.Text,
            focusedContainerColor = Palette.Text,
            focusedContentColor = Palette.Background,
            pressedContainerColor = Palette.TextSoft,
            pressedContentColor = Palette.Background,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = focusedScale),
        border = ClickableSurfaceDefaults.border(
            border = if (selected) Border(border = BorderStroke(2.dp, Palette.Accent), shape = shape) else Border.None,
            focusedBorder = Border(border = BorderStroke(3.dp, Palette.Accent), shape = shape),
        ),
        content = content,
    )
}

@Composable
fun ProgressBar(progress: Float, modifier: Modifier = Modifier, height: Dp = 4.dp, track: Color = Palette.Divider) {
    Box(
        modifier
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(track)
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(height)
                .background(Palette.Accent)
        )
    }
}

/** The ShedTV logo mark. */
@Composable
fun ShedMark(size: Dp) {
    androidx.compose.foundation.Image(
        painter = androidx.compose.ui.res.painterResource(R.drawable.shed_mark),
        contentDescription = "ShedTV",
        modifier = Modifier.size(size),
    )
}

/**
 * Fades a scrolling row out at its right edge, so a card that runs off the end reads as
 * "more this way" rather than being chopped off.
 */
fun Modifier.fadeRightEdge(width: Dp = 56.dp): Modifier = this
    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithContent {
        drawContent()
        val f = (width.toPx() / size.width).coerceIn(0f, 0.5f)
        drawRect(
            Brush.horizontalGradient(0f to Color.Black, (1f - f) to Color.Black, 1f to Color.Transparent),
            blendMode = BlendMode.DstIn,
        )
    }

/** Category names like "UK | Entertainment": the short prefix drawn small so the real name gets the space. */
fun categoryLabel(name: String): androidx.compose.ui.text.AnnotatedString {
    val i = name.indexOf('|')
    if (i !in 1..6) return androidx.compose.ui.text.AnnotatedString(name.trim())
    val prefix = name.substring(0, i).trim()
    val rest = name.substring(i + 1).trim().ifEmpty { return androidx.compose.ui.text.AnnotatedString(name.trim()) }
    return androidx.compose.ui.text.buildAnnotatedString {
        pushStyle(androidx.compose.ui.text.SpanStyle(fontSize = 0.72.em, fontWeight = FontWeight.Normal))
        append(prefix)
        append("  ")
        pop()
        append(rest)
    }
}

/** Muted live preview from the shared player. Shows a placeholder until the picture arrives. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun LivePreview(vm: com.matchday.tv.AppViewModel, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(Color.Black).touchTap({ vm.watchPreview() })) {
        androidx.compose.ui.viewinterop.AndroidView(
            factory = { ctx ->
                androidx.media3.ui.PlayerView(ctx).apply {
                    useController = false
                    resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    styleSubtitles(show = false)
                    player = vm.live.player
                }
            },
            onRelease = { it.player = null },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** Starts the preview once the highlight has rested on [channel] for a moment, so scrolling doesn't open a connection per row. */
@Composable
fun PreviewFollower(vm: com.matchday.tv.AppViewModel, channel: com.matchday.tv.data.Channel?) {
    LaunchedEffect(channel?.streamId, vm.live.stops, vm.previewEnabled, vm.previewMode) {
        val ch = channel ?: return@LaunchedEffect
        if (!vm.previewEnabled) return@LaunchedEffect
        // "Keep showing what I was watching": leave the preview alone while browsing.
        if (vm.previewMode == "stay" && vm.live.currentStreamId != null) return@LaunchedEffect
        kotlinx.coroutines.delay(1_000)
        vm.preview(ch)
    }
}

private val timeFormat = SimpleDateFormat("HH:mm", Locale.UK)
fun formatTime(millis: Long): String = timeFormat.format(Date(millis))

/** The current time in millis, refreshed every 15 seconds. */
@Composable
fun rememberNow(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            now = System.currentTimeMillis()
        }
    }
    return now
}


/** Small clock for the top-right corner (Settings → Clock). Updates on the minute. */
@Composable
fun ScreenClock(modifier: Modifier = Modifier) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000 - System.currentTimeMillis() % 60_000 + 50)
            now = System.currentTimeMillis()
        }
    }
    androidx.tv.material3.Text(
        formatTime(now), color = Palette.Text, fontSize = 18.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
        fontFamily = DisplayFamily, letterSpacing = 1.sp,
        modifier = modifier.background(Color(0xB307090C), androidx.compose.foundation.shape.RoundedCornerShape(8.dp)).then(Modifier.padding(horizontal = 10.dp, vertical = 3.dp)),
    )
}


/** Tap and long-press for phones and tablets (TV components only listen to the remote). */
fun Modifier.touchTap(onTap: () -> Unit, onLongPress: (() -> Unit)? = null): Modifier = this.then(
    Modifier.pointerInput(onTap, onLongPress) {
        detectTapGestures(onTap = { onTap() }, onLongPress = onLongPress?.let { l -> { _ -> l() } })
    },
)


/** Two-finger pinch: out to fill the screen, in to fit. Leaves one-finger taps and swipes alone. */
fun Modifier.pinchToFill(onFill: (Boolean) -> Unit): Modifier = this.then(
    Modifier.pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            var start = 0f
            while (true) {
                val event = awaitPointerEvent()
                val down = event.changes.filter { it.pressed }
                if (down.isEmpty()) break
                if (down.size >= 2) {
                    val d = (down[0].position - down[1].position).getDistance()
                    when {
                        start == 0f -> start = d
                        d > start * 1.25f -> { onFill(true); start = d }
                        d < start * 0.8f -> { onFill(false); start = d }
                    }
                } else start = 0f
            }
        }
    },
)

/**
 * When focus moves into this list from outside (Left from the page, Up from the episodes...), land on
 * [target] — the item you're on — instead of whichever item happens to be level with where you were.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
fun Modifier.enterAt(target: androidx.compose.ui.focus.FocusRequester): Modifier = this
    .focusProperties {
        enter = {
            if (runCatching { target.requestFocus() }.isSuccess) androidx.compose.ui.focus.FocusRequester.Cancel
            else androidx.compose.ui.focus.FocusRequester.Default
        }
    }
    .focusGroup()

/** True on phones and tablets; false on TV boxes (Fire TV, Google TV, Shield), which have no touch screen. */
@Composable
fun hasTouchScreen(): Boolean {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    return remember { ctx.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_TOUCHSCREEN) }
}

/**
 * Touch screens only: buttons to move the item you've picked up (hold to pick up). Remotes use up and down instead.
 * Not focusable, so they never get in the way of the remote.
 */
@Composable
fun TouchMoveButtons(onMove: (Int) -> Unit, onDone: () -> Unit) {
    if (!hasTouchScreen()) return
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf("⤒" to -10, "▲" to -1, "▼" to 1, "⤓" to 10).forEach { (label, by) ->
            Box(
                Modifier.size(width = 40.dp, height = 34.dp).background(Palette.Accent.copy(alpha = 0.18f), RoundedCornerShape(8.dp))
                    .touchTap({ onMove(by) }),
                contentAlignment = Alignment.Center,
            ) { androidx.tv.material3.Text(label, color = Palette.Accent, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        }
        Box(
            Modifier.height(34.dp).background(Palette.Accent, RoundedCornerShape(8.dp)).touchTap({ onDone() }).padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) { androidx.tv.material3.Text("Done", color = Palette.Background, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
    }
}
