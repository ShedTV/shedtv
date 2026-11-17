@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.matchday.tv.ui

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
    val Background = Color(0xFF07090C)
    val Card = Color(0xFF10151B)
    val CardRaised = Color(0xFF16202A)
    val Text = Color(0xFFF2F5F8)
    val TextSoft = Color(0xFFC9D3DE)
    val TextMuted = Color(0xFF8C9AAB)
    val Accent = Color(0xFF44C470)
    val Live = Color(0xFFFF3B3B)
    val Divider = Color(0x1FFFFFFF)
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
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
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
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(Color.Black)) {
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
    LaunchedEffect(channel?.streamId, vm.live.stops, vm.previewEnabled) {
        val ch = channel ?: return@LaunchedEffect
        if (!vm.previewEnabled) return@LaunchedEffect
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
