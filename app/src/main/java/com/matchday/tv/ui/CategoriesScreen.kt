@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.matchday.tv.ui

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
    val field = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(100); runCatching { field.requestFocus() } }
    BackHandler { vm.navigate(Screen.Settings) }

    val words = filter.lowercase().split(' ').filter { it.isNotBlank() }
    val all = vm.allCategories
    val shown = if (words.isEmpty()) all else all.filter { c -> words.all { c.name.lowercase().contains(it) } }
    val hidden = vm.hiddenCategories

    Column(Modifier.fillMaxSize().background(Palette.Background).padding(start = 40.dp, end = 32.dp, top = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column {
                Text("MANAGE CATEGORIES", color = Palette.Text, fontSize = 26.sp, fontWeight = FontWeight.Bold, fontFamily = DisplayFamily, letterSpacing = 1.sp)
                Text("${all.size - hidden.count { id -> all.any { it.id == id } }} of ${all.size} shown  ·  OK to show or hide  ·  favourites always show",
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
            ActionChip("Done") { vm.navigate(Screen.Settings) }
        }
        if (confirmAll) Text("That hides every category. Press Hide again to confirm.", color = Palette.Live, fontSize = 13.sp)

        LazyVerticalGrid(
            columns = GridCells.Adaptive(260.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 48.dp, start = 4.dp, end = 4.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(shown, key = { it.id }) { c ->
                val isShown = c.id !in hidden
                FocusTile(onClick = { vm.setCategoriesHidden(listOf(c.id), isShown) }, corner = 10.dp, focusedScale = 1.03f) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            Modifier.size(20.dp)
                                .background(if (isShown) Palette.Accent else Color.Transparent, RoundedCornerShape(5.dp))
                                .border(2.dp, if (isShown) Palette.Accent else Palette.TextMuted, RoundedCornerShape(5.dp)),
                            contentAlignment = Alignment.Center,
                        ) { if (isShown) Text("✓", color = Palette.Background, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                        Text(c.name, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
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
