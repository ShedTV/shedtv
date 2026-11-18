@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.matchday.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import kotlinx.coroutines.delay

/** Four-digit PIN entry for the parental lock. */
@Composable
fun PinPad(title: String, onCancel: () -> Unit, onDone: (String) -> Unit) {
    var pin by remember(title) { mutableStateOf("") }
    val first = remember { FocusRequester() }
    LaunchedEffect(title) { delay(60); runCatching { first.requestFocus() } }
    BackHandler { onCancel() }
    fun press(d: String) {
        if (pin.length >= 4) return
        pin += d
        if (pin.length == 4) { val p = pin; pin = ""; onDone(p) }
    }
    Box(
        Modifier.fillMaxSize().background(Color(0xD9000000)).onPreviewKeyEvent { e ->
            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            val c = e.key.nativeKeyCode
            when (c) {
                in android.view.KeyEvent.KEYCODE_0..android.view.KeyEvent.KEYCODE_9 -> { press((c - android.view.KeyEvent.KEYCODE_0).toString()); true }
                android.view.KeyEvent.KEYCODE_DEL -> { pin = pin.dropLast(1); true }
                else -> false
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.background(Palette.Card, RoundedCornerShape(18.dp)).padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("🔒  " + title, color = Palette.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(if (pin.isEmpty()) "• • • •" else pin.map { "●" }.joinToString(" ") + " •".repeat(4 - pin.length),
                color = Palette.Accent, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("⌫", "0", "✕")).forEachIndexed { r, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEachIndexed { i, k ->
                        FocusTile(
                            onClick = { when (k) { "⌫" -> pin = pin.dropLast(1); "✕" -> onCancel(); else -> press(k) } },
                            corner = 12.dp, focusedScale = 1.08f,
                            modifier = (if (r == 0 && i == 0) Modifier.focusRequester(first) else Modifier).size(64.dp),
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(k, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
            Text("Use the number keys or the pad above. ✕ or Back to cancel.", color = Palette.TextMuted, fontSize = 12.sp)
        }
    }
}
