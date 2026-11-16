@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.matchday.tv.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Text
import com.matchday.tv.AppViewModel
import com.matchday.tv.Screen

object Icons {
    const val LIVE = "M3 7h18v12H3zM8 3l4 4 4-4"
    const val GUIDE = "M3 5h18M3 12h18M3 19h18M8 5v14"
    const val MOVIES = "M4 4h16v16H4zM4 9h16M9 4v5M15 4v5"
    const val SERIES = "M3 6h18v12H3zM7 21h10"
}

/** A 24x24 stroke icon drawn from an SVG path, in the current content colour. */
@Composable
fun StrokeIcon(path: String, size: Dp = 24.dp, color: Color = LocalContentColor.current) {
    val p = remember(path) { PathParser().parsePathString(path).toPath() }
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension / 24f
        scale(s, s, pivot = androidx.compose.ui.geometry.Offset.Zero) {
            drawPath(p, color, style = Stroke(width = 1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

private data class RailItem(val label: String, val icon: String, val screen: Screen)

private val railItems = listOf(
    RailItem("Live TV", Icons.LIVE, Screen.Live),
    RailItem("Guide", Icons.GUIDE, Screen.Guide),
    RailItem("Movies", Icons.MOVIES, Screen.Movies),
    RailItem("Series", Icons.SERIES, Screen.Series),
)

@Composable
fun NavRail(vm: AppViewModel, current: Screen) {
    Column(
        Modifier
            .width(76.dp)
            .fillMaxHeight()
            .background(Color(0x99000000))
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier.size(34.dp).background(Palette.Accent, RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center,
        ) { Text("M", color = Palette.Background, fontWeight = FontWeight.Bold, fontSize = 18.sp) }
        Spacer(Modifier.height(18.dp))
        railItems.forEach { item ->
            val selected = item.screen == current
            FocusTile(
                onClick = { vm.navigate(item.screen) },
                selected = selected,
                corner = 12.dp,
                focusedScale = 1.06f,
                modifier = Modifier.width(62.dp),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    StrokeIcon(item.icon, 22.dp)
                    Text(item.label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/** Holding page for sections that arrive in a later build. */
@Composable
fun ComingSoonScreen(vm: AppViewModel, current: Screen, title: String, body: String) {
    androidx.activity.compose.BackHandler { vm.navigate(Screen.Live) }
    androidx.compose.foundation.layout.Row(Modifier.fillMaxSize().background(Palette.Background)) {
        NavRail(vm, current)
        Column(
            Modifier.fillMaxSize().padding(48.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(title.uppercase(), color = Palette.Text, fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.height(10.dp))
            Text(body, color = Palette.TextMuted, fontSize = 17.sp)
        }
    }
}
