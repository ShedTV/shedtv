@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.matchday.tv.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
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
    val Accent = Color(0xFF2EE6A6)
    val Live = Color(0xFFFF3B3B)
    val Divider = Color(0x1FFFFFFF)
}

@Composable
fun MatchdayTheme(content: @Composable () -> Unit) {
    MaterialTheme(
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

private class ShedShape(val color: Long, val points: List<Pair<Float, Float>>)

private val shedShapes = listOf(
        ShedShape(0xFF2EE6A6, listOf(10.0f to 46.0f, 50.0f to 25.0f, 90.0f to 46.0f, 90.0f to 86.0f, 10.0f to 86.0f)),
        ShedShape(0xFF25C48D, listOf(19.1f to 46.8f, 20.9f to 46.8f, 20.9f to 86.0f, 19.1f to 86.0f)),
        ShedShape(0xFF25C48D, listOf(29.1f to 41.5f, 30.9f to 41.5f, 30.9f to 86.0f, 29.1f to 86.0f)),
        ShedShape(0xFF25C48D, listOf(69.1f to 41.5f, 70.9f to 41.5f, 70.9f to 86.0f, 69.1f to 86.0f)),
        ShedShape(0xFF25C48D, listOf(79.1f to 46.8f, 80.9f to 46.8f, 80.9f to 86.0f, 79.1f to 86.0f)),
        ShedShape(0xFF138A62, listOf(0.0f to 46.0f, 50.0f to 17.0f, 100.0f to 46.0f, 100.0f to 53.0f, 50.0f to 24.0f, 0.0f to 53.0f)),
        ShedShape(0xFF1BA977, listOf(39.0f to 55.0f, 61.0f to 55.0f, 61.0f to 86.0f, 39.0f to 86.0f)),
        ShedShape(0xFF138A62, listOf(39.0f to 59.0f, 61.0f to 59.0f, 61.0f to 62.0f, 39.0f to 62.0f)),
        ShedShape(0xFF138A62, listOf(39.0f to 79.0f, 61.0f to 79.0f, 61.0f to 82.0f, 39.0f to 82.0f)),
        ShedShape(0xFF138A62, listOf(39.0f to 79.0f, 57.5f to 59.0f, 61.0f to 59.0f, 61.0f to 62.0f, 42.5f to 82.0f, 39.0f to 82.0f)),
        ShedShape(0xFF07090C, listOf(44.0f to 33.0f, 56.0f to 33.0f, 56.0f to 43.0f, 44.0f to 43.0f)),
        ShedShape(0xFF2EE6A6, listOf(46.0f to 35.0f, 54.0f to 35.0f, 54.0f to 41.0f, 46.0f to 41.0f)),
        ShedShape(0xFF138A62, listOf(4.0f to 86.0f, 96.0f to 86.0f, 96.0f to 91.0f, 4.0f to 91.0f)),
)

/** The ShedTV logo mark, drawn from the same geometry as the launcher icon. */
@Composable
fun ShedMark(size: Dp) {
    androidx.compose.foundation.Canvas(Modifier.size(size)) {
        val s = this.size.minDimension / 100f
        shedShapes.forEach { shape ->
            val path = androidx.compose.ui.graphics.Path()
            shape.points.forEachIndexed { i, (x, y) ->
                if (i == 0) path.moveTo(x * s, y * s) else path.lineTo(x * s, y * s)
            }
            path.close()
            drawPath(path, Color(shape.color))
        }
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
