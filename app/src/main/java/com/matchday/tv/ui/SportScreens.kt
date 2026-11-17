@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.matchday.tv.ui

import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
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
import com.matchday.tv.SportHub
import com.matchday.tv.data.Fixture
import com.matchday.tv.data.RUGBY_LEAGUE
import com.matchday.tv.data.Team
import com.matchday.tv.data.TeamsCatalog
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A club crest, or a badge in the team's colours until the crest is known. */
@Composable
fun TeamBadge(hub: SportHub, team: Team?, fallbackName: String, size: Dp) {
    val url = hub.badgeOf(team)
    val bg = team?.let { Color(it.colour) } ?: Palette.CardRaised
    val fg = team?.let { Color(it.textColour) } ?: Palette.TextSoft
    Box(
        Modifier.size(size).clip(CircleShape).background(if (url != null) Color(0x14FFFFFF) else bg),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(size / 10))
        } else {
            val label = team?.short ?: fallbackName.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1) }.uppercase()
            Text(label, color = fg, fontSize = (size.value / 3.2f).sp, fontWeight = FontWeight.Bold)
        }
    }
}

private val dayFmt = SimpleDateFormat("EEE", Locale.UK)

private fun whenLabel(start: Long, now: Long): String {
    val tz = java.util.TimeZone.getDefault()
    val day = 86_400_000L
    val diff = (start + tz.getOffset(start)) / day - (now + tz.getOffset(now)) / day
    return when (diff) {
        0L -> "Today"
        1L -> "Tomorrow"
        else -> dayFmt.format(Date(start))
    }
}

// ---------------- Sport Hub ----------------

@Composable
fun SportHubScreen(vm: AppViewModel) {
    val hub = vm.sport
    val now = rememberNow()
    val firstFocus = remember { FocusRequester() }

    // Re-scan the guide whenever it changes, your teams change, or a minute passes.
    LaunchedEffect(vm.guide, hub.followed, now / 60_000, vm.favourites) {
        hub.refresh(vm.channels, vm.guide, vm.favourites)
    }
    LaunchedEffect(hub.followed) { hub.requestBadges(hub.followed) }
    LaunchedEffect(Unit) { delay(120); runCatching { firstFocus.requestFocus() } }

    val live = hub.live
    val upcoming = hub.upcoming
    val featured = live.firstOrNull { it.involves(hub.followedKeys) } ?: live.firstOrNull()

    Row(Modifier.fillMaxSize().background(Palette.Background)) {
        NavRail(vm, Screen.Sport)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 36.dp, top = 24.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            // Header
            Row(Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("SPORT", color = Palette.Text, fontSize = 28.sp, fontWeight = FontWeight.Bold, fontFamily = DisplayFamily, letterSpacing = 1.sp)
                Spacer(Modifier.width(14.dp))
                Text(SimpleDateFormat("EEEE d MMMM", Locale.UK).format(Date(now)), color = Palette.TextMuted, fontSize = 15.sp)
                Spacer(Modifier.weight(1f))
                vm.guideStatus?.let { Text(it, color = Palette.TextMuted, fontSize = 12.sp, modifier = Modifier.padding(end = 14.dp)) }
                if (vm.updater.available) {
                    FocusTile(onClick = { vm.navigate(Screen.Settings) }, corner = 10.dp, focusedScale = 1.05f) {
                        Text("Update available", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Palette.Accent,
                            modifier = Modifier.background(Color(0x1AFFFFFF), RoundedCornerShape(10.dp)).padding(horizontal = 16.dp, vertical = 8.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                }
                FocusTile(onClick = { vm.navigate(Screen.Teams) }, corner = 10.dp, focusedScale = 1.05f,
                    modifier = if (featured == null && hub.followed.isNotEmpty()) Modifier.focusRequester(firstFocus) else Modifier) {
                    Text("My teams (${hub.followed.size})", fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.background(Color(0x1AFFFFFF), RoundedCornerShape(10.dp)).padding(horizontal = 16.dp, vertical = 8.dp))
                }
                Spacer(Modifier.width(14.dp))
                Text(formatTime(now), color = Palette.Text, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, fontFamily = DisplayFamily)
            }

            if (hub.followed.isEmpty()) {
                FocusTile(onClick = { vm.navigate(Screen.Teams) }, corner = 18.dp, focusedScale = 1.02f, modifier = Modifier.fillMaxWidth().focusRequester(firstFocus)) {
                    Column(Modifier.fillMaxWidth().background(Palette.Card).padding(28.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Pick your teams", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Palette.Text)
                        Text("Choose your football and rugby league teams and ShedTV will find their matches in your TV guide.", fontSize = 15.sp, color = Palette.TextSoft)
                        Text("Press OK to choose", fontSize = 13.sp, color = Palette.Accent, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            featured?.let { f ->
                FeaturedMatch(vm, f, now, hub.comingUp, Modifier.focusRequester(firstFocus))
            }

            Section(if (live.isEmpty()) "Live now" else "Live now  ·  ${live.size}") {
                if (live.isEmpty()) {
                    Empty(if (vm.guide.isEmpty()) "Waiting for the TV guide…" else "No live football or rugby league in your guide right now.")
                } else {
                    LazyRow(modifier = Modifier.fadeRightEdge(), horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(vertical = 10.dp, horizontal = 16.dp)) {
                        items(live, key = { it.channel.streamId + it.entry.start }) { f -> LiveCard(vm, f, now) }
                    }
                }
            }

            Section("Your teams  ·  coming up") {
                when {
                    hub.followed.isEmpty() -> Empty("Pick your teams to see their upcoming matches here.")
                    upcoming.isEmpty() -> Empty("Nothing found in the next 7 days of your TV guide yet.")
                    else -> LazyRow(modifier = Modifier.fadeRightEdge(), horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(vertical = 10.dp, horizontal = 16.dp)) {
                        items(upcoming, key = { it.channel.streamId + it.entry.start }) { f -> UpcomingCard(vm, f, now) }
                    }
                }
            }

            if (hub.followed.isNotEmpty()) {
                Section("My teams") {
                    LazyRow(modifier = Modifier.fadeRightEdge(), horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(vertical = 10.dp, horizontal = 16.dp)) {
                        items(hub.followed, key = { it.key }) { t ->
                            FocusTile(onClick = { vm.navigate(Screen.Teams) }, corner = 14.dp, focusedScale = 1.06f) {
                                Column(Modifier.width(96.dp).padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TeamBadge(hub, t, t.name, 52.dp)
                                    Text(t.name.substringBefore(" ("), fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 2, textAlign = TextAlign.Center)
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
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(modifier = Modifier.padding(start = 16.dp), text = title.uppercase(), color = Palette.Text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, fontFamily = DisplayFamily, letterSpacing = 1.sp)
        content()
    }
}

@Composable
private fun Empty(text: String) {
    Text(text, color = Palette.TextMuted, fontSize = 14.sp, modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 10.dp))
}

@Composable
private fun LiveBadge() {
    Text(
        "LIVE", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
        modifier = Modifier.background(Palette.Live, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** A short display name that fits big type: "Tottenham" rather than "Tottenham Hotspur". */
private fun displayName(team: Team?, raw: String): String {
    val name = team?.name?.substringBefore(" (") ?: return raw
    if (name.length <= 14) return name
    return team.aliases.filter { it.length in 4..14 }.maxByOrNull { it.length } ?: name
}

/** The glow behind the featured match, in a team colour that isn't too dark or too pale. */
private fun glowColour(f: Fixture): Color =
    listOfNotNull(f.homeTeam?.colour, f.awayTeam?.colour).map { Color(it) }
        .firstOrNull { it.luminance() in 0.03f..0.75f } ?: Palette.Accent

private fun minsLeft(f: Fixture, now: Long) = ((f.entry.end - now) / 60_000).coerceAtLeast(0)

@Composable
private fun FeaturedMatch(vm: AppViewModel, f: Fixture, now: Long, comingUp: List<Fixture>, modifier: Modifier) {
    val hub = vm.sport
    val glow by animateColorAsState(glowColour(f), tween(700), label = "glow")
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).drawBehind {
            // An oval that fades to nothing before every edge, so the glow never shows a border.
            val r = size.height * 0.62f
            val stretch = (size.width * 0.42f) / r
            val c = Offset(size.width * 0.33f, size.height * 0.5f)
            scale(scaleX = stretch, scaleY = 1f, pivot = c) {
                drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = 0.24f), Color.Transparent), center = c, radius = r), radius = r, center = c)
            }
        }
    ) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LiveBadge()
                    Text(
                        listOf(f.competition, vm.channelLabel(f.channel)).filter { it.isNotBlank() }.joinToString("  ·  "),
                        color = Palette.TextSoft, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TeamBadge(hub, f.homeTeam, f.home, 42.dp)
                    Text(displayName(f.homeTeam, f.home).uppercase(), color = Palette.Text, fontSize = 32.sp, fontWeight = FontWeight.Bold,
                        fontFamily = DisplayFamily, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Text("V", color = Palette.Accent, fontSize = 30.sp, fontWeight = FontWeight.Bold, fontFamily = DisplayFamily)
                    Text(displayName(f.awayTeam, f.away).uppercase(), color = Palette.Text, fontSize = 32.sp, fontWeight = FontWeight.Bold,
                        fontFamily = DisplayFamily, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    TeamBadge(hub, f.awayTeam, f.away, 42.dp)
                }
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("KO ${formatTime(f.entry.start)}", color = Palette.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, fontFamily = DisplayFamily)
                    Text("${minsLeft(f, now)} min left", color = Palette.Accent, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, fontFamily = DisplayFamily)
                    Text("ends ${formatTime(f.entry.end)}", color = Palette.TextMuted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 2.dp))
                }
                ProgressBar(f.entry.progress(now), Modifier.fillMaxWidth(0.9f), height = 4.dp)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(top = 4.dp)) {
                    FocusTile(onClick = { vm.play(f.channel, from = Screen.Sport) }, corner = 10.dp, focusedScale = 1.05f, modifier = modifier) {
                        Text("▶  Watch live", fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.background(Color(0x26FFFFFF), RoundedCornerShape(10.dp)).padding(horizontal = 22.dp, vertical = 10.dp))
                    }
                    if (f.alternatives.isNotEmpty()) {
                        Text("+${f.alternatives.size} backup stream" + if (f.alternatives.size == 1) "" else "s", color = Palette.TextMuted, fontSize = 12.sp)
                    }
                }
            }
            ComingUpPanel(vm, comingUp, now)
        }
    }
}

/** Beside the featured match: what's starting soon, so you can plan the afternoon. */
@Composable
private fun ComingUpPanel(vm: AppViewModel, list: List<Fixture>, now: Long) {
    Column(
        Modifier.width(280.dp).background(Color(0xCC10151B), RoundedCornerShape(16.dp))
            .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(16.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("COMING UP", color = Palette.TextMuted, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = DisplayFamily,
            letterSpacing = 1.5.sp, modifier = Modifier.padding(start = 6.dp, bottom = 4.dp))
        if (list.isEmpty()) {
            Text("No more matches in the next 12 hours of your guide.", color = Palette.TextMuted, fontSize = 12.sp, modifier = Modifier.padding(6.dp))
        }
        list.forEach { f ->
            FocusTile(onClick = { vm.play(f.channel, from = Screen.Sport) }, corner = 10.dp, focusedScale = 1.03f, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.width(46.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(formatTime(f.entry.start), fontSize = 19.sp, fontWeight = FontWeight.Bold, fontFamily = DisplayFamily)
                        if (whenLabel(f.entry.start, now) != "Today") {
                            Text(whenLabel(f.entry.start, now).uppercase(), fontSize = 9.sp, fontWeight = FontWeight.SemiBold,
                                color = LocalContentColor.current.copy(alpha = 0.6f))
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(f.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(vm.channelLabel(f.channel), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = LocalContentColor.current.copy(alpha = 0.65f))
                    }
                    if (f.involves(vm.sport.followedKeys)) Text("★", fontSize = 12.sp, color = Palette.Accent)
                }
            }
        }
    }
}

@Composable
private fun LiveCard(vm: AppViewModel, f: Fixture, now: Long) {
    val hub = vm.sport
    val mine = f.involves(hub.followedKeys)
    FocusTile(onClick = { vm.play(f.channel, from = Screen.Sport) }, corner = 16.dp, focusedScale = 1.05f) {
        Column(
            Modifier.width(250.dp).background(Palette.Card)
                .then(if (mine) Modifier.border(2.dp, Palette.Accent.copy(alpha = 0.6f), RoundedCornerShape(16.dp)) else Modifier)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LiveBadge()
                Text(f.competition.ifBlank { if (f.homeTeam?.sport == RUGBY_LEAGUE) "Rugby league" else "Football" }, color = Palette.TextMuted,
                    fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text("${minsLeft(f, now)}m left", color = Color(0xFFFF6B6B), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            TeamLine(hub, f.homeTeam, f.home)
            TeamLine(hub, f.awayTeam, f.away)
            ProgressBar(f.entry.progress(now), Modifier.fillMaxWidth(), height = 4.dp)
            Text(vm.channelLabel(f.channel), color = Palette.TextMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun TeamLine(hub: SportHub, team: Team?, name: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TeamBadge(hub, team, name, 30.dp)
        Text(displayName(team, name), color = Palette.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun UpcomingCard(vm: AppViewModel, f: Fixture, now: Long) {
    val hub = vm.sport
    FocusTile(onClick = { vm.play(f.channel, from = Screen.Sport) }, corner = 16.dp, focusedScale = 1.05f) {
        Row(
            Modifier.width(300.dp).background(Palette.Card).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(Modifier.width(78.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(whenLabel(f.entry.start, now).uppercase(), color = Palette.TextMuted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(formatTime(f.entry.start), color = Palette.Text, fontSize = 28.sp, fontWeight = FontWeight.Bold, fontFamily = DisplayFamily)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TeamBadge(hub, f.homeTeam, f.home, 26.dp)
                    TeamBadge(hub, f.awayTeam, f.away, 26.dp)
                }
                Text(f.title, color = Palette.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(vm.channelLabel(f.channel), color = Palette.TextMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// ---------------- Team picker ----------------

private const val TAB_SEARCH = "search"
private const val TAB_MINE = "mine"

@Composable
fun TeamPickerScreen(vm: AppViewModel) {
    val hub = vm.sport
    var tab by rememberSaveable { mutableStateOf(TeamsCatalog.leagues.first().id) }
    var query by rememberSaveable { mutableStateOf("") }
    val firstTab = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(100); runCatching { firstTab.requestFocus() } }
    BackHandler { vm.navigate(Screen.Sport) }

    val teams: List<Team> = when (tab) {
        TAB_SEARCH -> hub.searchResults
        TAB_MINE -> hub.followed
        else -> TeamsCatalog.leagues.firstOrNull { it.id == tab }?.teams.orEmpty()
    }
    LaunchedEffect(tab, teams.size) { hub.requestBadges(teams) }

    val tabs = TeamsCatalog.leagues.map { it.id to it.name } + listOf(TAB_SEARCH to "Search all teams", TAB_MINE to "My teams (${hub.followed.size})")

    Row(Modifier.fillMaxSize().background(Palette.Background).padding(start = 40.dp, end = 32.dp, top = 32.dp)) {
        Column(Modifier.width(240.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("MY TEAMS", color = Palette.Text, fontSize = 28.sp, fontWeight = FontWeight.Bold, fontFamily = DisplayFamily, letterSpacing = 1.sp)
            Text("OK to follow or unfollow", color = Palette.TextMuted, fontSize = 13.sp)
            Text(hub.badgeStatus.orEmpty(), color = Palette.Accent, fontSize = 12.sp, modifier = Modifier.padding(bottom = 12.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(4.dp), modifier = Modifier.weight(1f)) {
                itemsIndexed(tabs, key = { _, t -> t.first }) { i, (id, name) ->
                    FocusTile(
                        onClick = { tab = id }, selected = tab == id, corner = 10.dp,
                        modifier = Modifier.fillMaxWidth()
                            .then(if (i == 0) Modifier.focusRequester(firstTab) else Modifier)
                            .onFocusChanged { if (it.isFocused) tab = id },
                    ) {
                        Text(name, fontSize = 15.sp, fontWeight = if (tab == id) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp))
                    }
                }
            }
            FocusTile(onClick = { vm.navigate(Screen.Sport) }, corner = 10.dp, modifier = Modifier.padding(vertical = 16.dp)) {
                Text("✓  Done", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.background(Color(0x22FFFFFF), RoundedCornerShape(10.dp)).padding(horizontal = 22.dp, vertical = 10.dp))
            }
        }

        Spacer(Modifier.width(28.dp))

        Column(Modifier.weight(1f).fillMaxHeight()) {
            if (tab == TAB_SEARCH) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 14.dp)) {
                    OutlinedTextField(
                        value = query, onValueChange = { query = it }, singleLine = true,
                        label = { androidx.compose.material3.Text("Team name, e.g. Wigan Athletic") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { hub.search(query) }),
                        modifier = Modifier.width(380.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Palette.Text, unfocusedTextColor = Palette.Text,
                            focusedBorderColor = Palette.Accent, unfocusedBorderColor = Palette.TextMuted,
                            focusedLabelColor = Palette.Accent, unfocusedLabelColor = Palette.TextMuted, cursorColor = Palette.Accent,
                            focusedContainerColor = Palette.Card, unfocusedContainerColor = Palette.Card,
                        ),
                    )
                    FocusTile(onClick = { hub.search(query) }, corner = 10.dp) {
                        Text(if (hub.searching) "Searching…" else "Search", fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.background(Color(0x22FFFFFF), RoundedCornerShape(10.dp)).padding(horizontal = 18.dp, vertical = 10.dp))
                    }
                }
                hub.searchMessage?.let { Text(it, color = Palette.TextMuted, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp)) }
            } else {
                Text(tabs.firstOrNull { it.first == tab }?.second.orEmpty(), color = Palette.TextSoft, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 14.dp, top = 6.dp))
            }
            if (tab == TAB_MINE && teams.isEmpty()) {
                Text("You're not following any teams yet.", color = Palette.TextMuted, fontSize = 15.sp)
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(120.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 32.dp, top = 6.dp, start = 4.dp, end = 4.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(teams, key = { it.key }) { t -> TeamTile(hub, t) }
            }
        }
    }
}

@Composable
private fun TeamTile(hub: SportHub, team: Team) {
    val following = hub.isFollowed(team)
    FocusTile(onClick = { hub.toggle(team) }, corner = 14.dp, focusedScale = 1.06f, selected = following) {
        Box {
            Column(
                Modifier.fillMaxWidth().height(138.dp)
                    .then(if (following) Modifier.border(2.dp, Palette.Accent, RoundedCornerShape(14.dp)) else Modifier)
                    .padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                TeamBadge(hub, team, team.name, 64.dp)
                Spacer(Modifier.height(10.dp))
                Text(team.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis)
                if (team.key.startsWith("tsdb-") && team.league.isNotBlank()) {
                    Text(team.league, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = LocalContentColor.current.copy(alpha = 0.6f))
                }
            }
            if (following) {
                Box(Modifier.align(Alignment.TopEnd).padding(8.dp).size(22.dp).background(Palette.Accent, CircleShape), contentAlignment = Alignment.Center) {
                    Text("✓", color = Palette.Background, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
