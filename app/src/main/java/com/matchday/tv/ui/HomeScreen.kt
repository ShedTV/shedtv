@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.matchday.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.matchday.tv.AppViewModel
import com.matchday.tv.Screen
import com.matchday.tv.WatchItem
import com.matchday.tv.WatchMatch
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dayFormat = SimpleDateFormat("EEE", Locale.UK)

/** Coloured initials for a team, or a star for words. Never a club crest. */
@Composable
fun WatchMark(item: WatchItem, size: Dp) {
    val bg = item.colour?.let { Color(it) } ?: Palette.CardRaised
    val fg = item.textColour?.let { Color(it) } ?: Palette.Accent
    Box(Modifier.size(size).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        Text(item.short ?: "★", color = fg, fontSize = (size.value * 0.34f).sp, fontWeight = FontWeight.Bold, fontFamily = DisplayFamily, maxLines = 1)
    }
}

@Composable
fun HomeScreen(vm: AppViewModel) {
    val now = rememberNow()
    val wl = vm.watchlist
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(120); runCatching { first.requestFocus() } }
    androidx.activity.compose.BackHandler { vm.goHome() }
    var firstUsed = false
    fun firstMod(): Modifier = if (!firstUsed) { firstUsed = true; Modifier.focusRequester(first) } else Modifier

    Row(Modifier.fillMaxSize().background(Palette.Background)) {
        NavRail(vm, Screen.Home)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 28.dp, top = 24.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("HOME", color = Palette.Text, fontSize = 28.sp, fontWeight = FontWeight.Bold, fontFamily = DisplayFamily, letterSpacing = 1.sp)
                SmallChip("★  Watchlist (${wl.items.size})", Modifier) { vm.navigate(Screen.Watchlist) }
            }

            // On now
            Section("On now from your watchlist") {
                if (wl.items.isEmpty()) {
                    EmptyCard("Add shows, teams or anything else you like to your watchlist, and ShedTV picks them out of your guide here.", "Set up your watchlist", firstMod()) { vm.navigate(Screen.Watchlist) }
                } else if (wl.onNow.isEmpty()) {
                    Text("Nothing from your watchlist is on right now.", color = Palette.TextMuted, fontSize = 14.sp, modifier = Modifier.padding(end = 28.dp))
                } else Row {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(end = 28.dp, top = 4.dp, bottom = 4.dp)) {
                        items(wl.onNow, key = { it.channel.uid + it.entry.start }) { m -> MatchCard(vm, m, now, live = true, modifier = firstMod()) }
                    }
                }
            }

            if (wl.comingUp.isNotEmpty()) Section("Coming up") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(end = 28.dp, top = 4.dp, bottom = 4.dp)) {
                    items(wl.comingUp, key = { "c" + it.channel.uid + it.entry.start }) { m -> MatchCard(vm, m, now, live = false, modifier = firstMod()) }
                }
            }

            val cont = vm.continueWatching()
            if (cont.isNotEmpty()) Section("Continue watching") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(end = 28.dp, top = 4.dp, bottom = 4.dp)) {
                    items(cont, key = { "w" + it.id }) { e ->
                        FocusTile(onClick = { vm.resume(e) }, corner = 12.dp, focusedScale = 1.05f, modifier = firstMod().width(210.dp)) {
                            Column {
                                Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Palette.CardRaised)) {
                                    if (e.image != null) AsyncImage(model = e.image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                }
                                ProgressBar(e.progress, Modifier.fillMaxWidth(), height = 3.dp)
                                Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                    Text(e.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (e.subtitle.isNotBlank()) Text(e.subtitle, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = LocalContentColor.current.copy(alpha = 0.7f))
                                }
                            }
                        }
                    }
                }
            }

            val favs = vm.favouriteChannels()
            Section("Favourite channels") {
                if (favs.isEmpty()) {
                    Text("Hold OK on any channel in Live TV or the guide to add it to your favourites.", color = Palette.TextMuted, fontSize = 14.sp, modifier = Modifier.padding(end = 28.dp))
                } else LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(end = 28.dp, top = 4.dp, bottom = 4.dp)) {
                    items(favs, key = { "f" + it.uid }) { ch ->
                        val prog = vm.programmesFor(ch).firstOrNull { it.isOn(now) }
                        FocusTile(onClick = { vm.play(ch, from = Screen.Home) }, onLongClick = { vm.toggleFavourite(ch) }, corner = 12.dp, focusedScale = 1.05f, modifier = firstMod().width(220.dp)) {
                            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                ChannelLogo(ch, 40)
                                Column(Modifier.weight(1f)) {
                                    Text(vm.channelLabel(ch), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(prog?.title ?: "", fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = LocalContentColor.current.copy(alpha = 0.7f))
                                    if (prog != null) ProgressBar(((now - prog.start).toFloat() / (prog.end - prog.start).coerceAtLeast(1)).coerceIn(0f, 1f), Modifier.fillMaxWidth().padding(top = 4.dp), height = 3.dp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title.uppercase(), color = Palette.TextSoft, fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = DisplayFamily, letterSpacing = 1.sp)
        content()
    }
}

@Composable
private fun MatchCard(vm: AppViewModel, m: WatchMatch, now: Long, live: Boolean, modifier: Modifier) {
    val canStart = live && vm.canCatchUp(m.channel, m.entry, now)
    FocusTile(
        onClick = { vm.play(m.channel, from = Screen.Home) },
        onLongClick = if (canStart) ({ vm.playCatchup(m.channel, m.entry, Screen.Home) }) else null,
        corner = 14.dp, focusedScale = 1.05f, modifier = modifier.width(300.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                WatchMark(m.item, 30.dp)
                Text(m.item.label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = LocalContentColor.current.copy(alpha = 0.75f), modifier = Modifier.weight(1f))
                if (live) Text("● NOW", color = Palette.Live, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Text(m.entry.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val time = if (live) "${formatTime(m.entry.start)}–${formatTime(m.entry.end)}" else {
                val sameDay = dayFormat.format(Date(m.entry.start)) == dayFormat.format(Date(now))
                (if (sameDay) "" else dayFormat.format(Date(m.entry.start)) + " ") + formatTime(m.entry.start)
            }
            Text("${vm.channelLabel(m.channel)}  ·  $time", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = LocalContentColor.current.copy(alpha = 0.75f))
            if (live) ProgressBar(((now - m.entry.start).toFloat() / (m.entry.end - m.entry.start).coerceAtLeast(1)).coerceIn(0f, 1f), Modifier.fillMaxWidth(), height = 3.dp)
            if (canStart) Text("Hold OK to watch from the start", fontSize = 11.sp, color = LocalContentColor.current.copy(alpha = 0.6f))
        }
    }
}

@Composable
private fun EmptyCard(text: String, action: String, modifier: Modifier, onClick: () -> Unit) {
    FocusTile(onClick = onClick, corner = 14.dp, focusedScale = 1.03f, modifier = modifier.width(520.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text, fontSize = 14.sp)
            Text("$action  →", fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun SmallChip(label: String, modifier: Modifier = Modifier, selected: Boolean = false, onClick: () -> Unit) {
    FocusTile(onClick = onClick, selected = selected, corner = 10.dp, focusedScale = 1.05f, modifier = modifier) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
            modifier = Modifier.background(if (selected) Color.Transparent else Color(0x1AFFFFFF), RoundedCornerShape(10.dp)).padding(horizontal = 14.dp, vertical = 8.dp))
    }
}

@Composable
fun WatchlistScreen(vm: AppViewModel) {
    val wl = vm.watchlist
    val adding = androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    val text = androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf("") }
    val confirm = remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(120); runCatching { first.requestFocus() } }
    androidx.activity.compose.BackHandler { if (adding.value) adding.value = false else vm.navigate(Screen.Home) }

    Row(Modifier.fillMaxSize().background(Palette.Background)) {
        NavRail(vm, Screen.Home)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 28.dp, end = 32.dp, top = 24.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("WATCHLIST", color = Palette.Text, fontSize = 28.sp, fontWeight = FontWeight.Bold, fontFamily = DisplayFamily, letterSpacing = 1.sp)
            Text("ShedTV looks for these in your TV guide and shows them on Home. You'll also get a reminder just before one starts (press ☰ or hold OK on the reminder to jump to it). Works for anything: a team, a show, an event or a person.",
                color = Palette.TextSoft, fontSize = 14.sp)

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                SmallChip("+  Add words", Modifier.focusRequester(first)) { adding.value = true }
                Text("e.g. Gladiators, Match of the Day, Formula 1", color = Palette.TextMuted, fontSize = 12.sp)
            }
            if (adding.value) {
                val field = remember { FocusRequester() }
                LaunchedEffect(Unit) { delay(80); runCatching { field.requestFocus() } }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    androidx.compose.material3.OutlinedTextField(
                        value = text.value, onValueChange = { text.value = it }, singleLine = true,
                        label = { androidx.compose.material3.Text("Words to look for") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = {
                            if (wl.addWords(text.value)) { text.value = ""; adding.value = false; vm.refreshWatchlist() }
                        }),
                        modifier = Modifier.width(420.dp).focusRequester(field),
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Palette.Text, unfocusedTextColor = Palette.Text, focusedBorderColor = Palette.Accent,
                            unfocusedBorderColor = Palette.TextMuted, focusedLabelColor = Palette.Accent, unfocusedLabelColor = Palette.TextMuted,
                            cursorColor = Palette.Accent, focusedContainerColor = Palette.Card, unfocusedContainerColor = Palette.Card,
                        ),
                    )
                    SmallChip("Add") { if (wl.addWords(text.value)) { text.value = ""; adding.value = false; vm.refreshWatchlist() } }
                    SmallChip("Cancel") { adding.value = false }
                }
            }

            if (wl.items.isNotEmpty()) {
                Text("ON YOUR WATCHLIST  ·  hold OK to remove  ·  ◀◀ ▶▶ to move", color = Palette.TextSoft, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = DisplayFamily, letterSpacing = 1.sp)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    wl.items.forEach { w ->
                        FocusTile(
                            onClick = { confirm.value = null },
                            onLongClick = { if (confirm.value == w.id) { wl.remove(w.id); confirm.value = null; vm.refreshWatchlist() } else confirm.value = w.id },
                            corner = 12.dp, focusedScale = 1.02f,
                            modifier = Modifier.width(560.dp).onPreviewKeyEventMove { by -> wl.move(w.id, by) },
                        ) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                WatchMark(w, 30.dp)
                                Text(w.label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                if (confirm.value == w.id) Text("Hold OK again to remove", color = Palette.Live, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                else if (w.phrases.size > 1) Text("also: " + w.phrases.drop(1).take(3).joinToString(", "), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    color = LocalContentColor.current.copy(alpha = 0.6f), modifier = Modifier.width(200.dp))
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            Text("QUICK ADD: TEAMS", color = Palette.TextSoft, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = DisplayFamily, letterSpacing = 1.sp)
            Text("Teams also match the other names guides use (Man Utd, Spurs and so on). OK to add or remove.", color = Palette.TextMuted, fontSize = 12.sp)
            com.matchday.tv.data.TeamsCatalog.leagues.forEach { league ->
                Text(league.name, color = Palette.TextMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 2.dp)) {
                    items(league.teams, key = { it.key }) { t ->
                        val on = wl.hasTeam(t)
                        FocusTile(onClick = { wl.toggleTeam(t); vm.refreshWatchlist() }, selected = on, corner = 20.dp, focusedScale = 1.06f) {
                            Row(Modifier.padding(start = 5.dp, end = 12.dp, top = 5.dp, bottom = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                WatchMark(WatchItem("", t.name, emptyList(), t.short, t.colour, t.textColour), 26.dp)
                                Text((if (on) "✓ " else "") + t.name, fontSize = 13.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** ◀◀ / ▶▶ (or channel up/down) move a watchlist item up or down. */
private fun Modifier.onPreviewKeyEventMove(move: (Int) -> Unit): Modifier = this.then(
    Modifier.onPreviewKeyEvent { e ->
        if (e.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) return@onPreviewKeyEvent false
        when (e.key.nativeKeyCode) {
            android.view.KeyEvent.KEYCODE_MEDIA_REWIND, android.view.KeyEvent.KEYCODE_CHANNEL_UP -> { move(-1); true }
            android.view.KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, android.view.KeyEvent.KEYCODE_CHANNEL_DOWN -> { move(1); true }
            else -> false
        }
    },
)
