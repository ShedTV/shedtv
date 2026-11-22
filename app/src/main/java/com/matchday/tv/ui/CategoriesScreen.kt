@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.matchday.tv.ui

import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.KeyEventType
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Text
import com.matchday.tv.AppViewModel
import com.matchday.tv.Screen
import kotlinx.coroutines.delay

/** Choose which provider categories appear in Live TV, the guide and search. */
@Composable
fun CategoriesScreen(vm: AppViewModel) {
    var filter by rememberSaveable { mutableStateOf("") }
    var confirmAll by remember { mutableStateOf(false) }
    // Category being moved (hold OK to pick up, arrows to move, OK to drop).
    var moving by remember { mutableStateOf<String?>(null) }
    val field = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(100); runCatching { field.requestFocus() } }
    BackHandler { if (moving != null) moving = null else vm.navigate(Screen.Settings) }

    val words = filter.lowercase().split(' ').filter { it.isNotBlank() }
    val all = vm.allCategories
    val shown = if (words.isEmpty()) all else all.filter { c -> words.all { c.name.lowercase().contains(it) } }
    val hidden = vm.hiddenCategories

    Column(Modifier.fillMaxSize().background(Palette.Background).padding(start = 40.dp, end = 32.dp, top = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column {
                Text("MANAGE CATEGORIES", color = Palette.Text, fontSize = 26.sp, fontWeight = FontWeight.Bold, fontFamily = DisplayFamily, letterSpacing = 1.sp)
                Text("${all.size - hidden.count { id -> all.any { it.id == id } }} of ${all.size} shown  ·  OK show/hide  ·  hold OK to move  ·  favourites always show",
                    color = Palette.TextMuted, fontSize = 13.sp)
            }
        }
        Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = filter, onValueChange = { filter = it; confirmAll = false }, singleLine = true,
                label = { androidx.compose.material3.Text("Filter, e.g. arabic, adult, usa, 4k") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, autoCorrectEnabled = false),
                modifier = Modifier.width(380.dp).focusRequester(field),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Palette.Text, unfocusedTextColor = Palette.Text,
                    focusedBorderColor = Palette.Accent, unfocusedBorderColor = Palette.TextMuted,
                    focusedLabelColor = Palette.Accent, unfocusedLabelColor = Palette.TextMuted, cursorColor = Palette.Accent,
                    focusedContainerColor = Palette.Card, unfocusedContainerColor = Palette.Card,
                ),
            )
            val label = if (words.isEmpty()) "these ${shown.size}" else "${shown.size} matching"
            ActionChip("Hide $label") {
                if (words.isEmpty() && !confirmAll) confirmAll = true
                else { vm.setCategoriesHidden(shown.map { it.id }, true); confirmAll = false }
            }
            ActionChip("Show $label") { vm.setCategoriesHidden(shown.map { it.id }, false) }
            if (vm.categoryOrder.isNotEmpty()) ActionChip("Reset order") { vm.resetCategoryOrder() }
            ActionChip("Done") { vm.navigate(Screen.Settings) }
        }
        if (confirmAll) Text("That hides every category. Press Hide again to confirm.", color = Palette.Live, fontSize = 13.sp)
        if (moving != null) {
            Text("Moving: ▲ up  ·  ▼ down  ·  ◀◀ ▶▶ 10 places  ·  OK to drop", color = Palette.Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        } else if (words.isNotEmpty()) {
            Text("Clear the filter to move categories.", color = Palette.TextMuted, fontSize = 12.sp)
        }

        // While moving, keep the category you're carrying on screen and selected. Without this it slid out
        // of view as it went up, the list dropped it, and the move stopped part-way.
        val listState = androidx.compose.foundation.lazy.rememberLazyListState()
        val movingFocus = remember { FocusRequester() }
        val movingIndex = shown.indexOfFirst { it.id == moving }
        LaunchedEffect(moving, movingIndex) {
            if (moving == null || movingIndex < 0) return@LaunchedEffect
            val info = listState.layoutInfo.visibleItemsInfo
            val first = info.firstOrNull()?.index ?: 0
            val last = info.lastOrNull()?.index ?: 0
            val count = (last - first + 1).coerceAtLeast(1)
            if (movingIndex <= first + 1) listState.scrollToItem((movingIndex - 2).coerceAtLeast(0))
            else if (movingIndex >= last - 1) listState.scrollToItem((movingIndex - count + 3).coerceAtLeast(0))
            runCatching { movingFocus.requestFocus() }
        }
        // One list, top to bottom, in the same order as Live TV: easy to follow with a remote.
        androidx.compose.foundation.lazy.LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 48.dp, start = 4.dp, end = 4.dp),
            modifier = Modifier.fillMaxHeight().widthIn(max = 760.dp).fillMaxWidth(),
        ) {
            items(shown, key = { it.id }) { c ->
                val isShown = c.id !in hidden
                val isMoving = moving == c.id
                FocusTile(
                    onClick = { if (isMoving) moving = null else vm.setCategoriesHidden(listOf(c.id), isShown) },
                    onLongClick = { if (words.isEmpty()) moving = c.id },
                    selected = isMoving, corner = 10.dp, focusedScale = if (isMoving) 1.03f else 1.01f,
                    modifier = Modifier.fillMaxWidth().then(if (isMoving) Modifier.focusRequester(movingFocus) else Modifier).onPreviewKeyEvent { e ->
                        if (!isMoving || e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (e.key.nativeKeyCode) {
                            android.view.KeyEvent.KEYCODE_DPAD_UP, android.view.KeyEvent.KEYCODE_DPAD_LEFT -> { vm.moveCategory(c.id, -1); true }
                            android.view.KeyEvent.KEYCODE_DPAD_DOWN, android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> { vm.moveCategory(c.id, 1); true }
                            android.view.KeyEvent.KEYCODE_MEDIA_REWIND -> { vm.moveCategory(c.id, -10); true }
                            android.view.KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { vm.moveCategory(c.id, 10); true }
                            else -> false
                        }
                    },
                ) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            Modifier.size(20.dp)
                                .background(if (isShown) Palette.Accent else Color.Transparent, RoundedCornerShape(5.dp))
                                .border(2.dp, if (isShown) Palette.Accent else Palette.TextMuted, RoundedCornerShape(5.dp)),
                            contentAlignment = Alignment.Center,
                        ) { if (isShown) Text("✓", color = Palette.Background, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                        Text(if (isMoving) "↕  " + c.name else c.name, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                            color = if (isShown) LocalContentColor.current else LocalContentColor.current.copy(alpha = 0.45f))
                        Text(vm.categoryChannelCount(c.id).toString(), fontSize = 12.sp, color = LocalContentColor.current.copy(alpha = 0.55f))
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionChip(label: String, onClick: () -> Unit) {
    FocusTile(onClick = onClick, corner = 10.dp, focusedScale = 1.05f) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
            modifier = Modifier.background(Color(0x1AFFFFFF), RoundedCornerShape(10.dp)).padding(horizontal = 16.dp, vertical = 9.dp))
    }
}
