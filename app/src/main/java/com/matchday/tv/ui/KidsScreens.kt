@file:OptIn(ExperimentalTvMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.matchday.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.matchday.tv.AppViewModel
import com.matchday.tv.Screen
import com.matchday.tv.data.Category
import kotlinx.coroutines.delay

/** What a parent needs to know: Kids mode can't stop uninstalling, so use the device's own controls too. */
@Composable
fun MakeItStick() {
    Column(
        Modifier.fillMaxWidth().background(Palette.Accent.copy(alpha = 0.08f), RoundedCornerShape(14.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text("Make it stick", color = Palette.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text("Kids mode keeps ShedTV child-friendly, but it can't stop someone uninstalling ShedTV, clearing its data or opening other apps. To stop that, use your device's own controls as well:",
            color = Palette.TextSoft, fontSize = 13.sp)
        Text("•  Fire TV: Settings → Preferences → Parental Controls. Turn them on and set a PIN.", color = Palette.TextSoft, fontSize = 13.sp)
        Text("•  Google TV, Chromecast and Google TV Streamer: add a Kids profile for your child and allow ShedTV in it. Children can't uninstall apps from a kids profile.", color = Palette.TextSoft, fontSize = 13.sp)
        Text("•  Shield and other Android TV boxes: Settings → Device Preferences → Security & restrictions → Create restricted profile, then allow ShedTV.", color = Palette.TextSoft, fontSize = 13.sp)
        Text("Menu names may differ slightly on your device. Use a different PIN from your ShedTV one, and keep both somewhere safe. More help: shedtv.app/kids",
            color = Palette.TextMuted, fontSize = 12.sp)
    }
}

@Composable
private fun PickList(title: String, help: String, cats: List<Category>, picked: Set<String>, onToggle: (String) -> Unit, firstMod: Modifier = Modifier) {
    Text(title, color = Palette.Accent, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = DisplayFamily, letterSpacing = 1.sp)
    Text(help, color = Palette.TextMuted, fontSize = 12.sp)
    if (cats.isEmpty()) {
        Text("None found (still loading, or this subscription doesn't have any).", color = Palette.TextMuted, fontSize = 13.sp)
        return
    }
    // Suggested ones first, then the rest.
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        cats.sortedByDescending { it.id in picked }.forEachIndexed { i, c ->
            val on = c.id in picked
            FocusTile(onClick = { onToggle(c.id) }, selected = on, corner = 10.dp, focusedScale = 1.05f, modifier = if (i == 0) firstMod else Modifier) {
                Text((if (on) "✓  " else "") + categoryLabel(c.name), fontSize = 13.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, maxLines = 1,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
            }
        }
    }
}

/** Choose what children can see, then switch Kids mode on. */
@Composable
fun KidsSetupScreen(vm: AppViewModel) {
    val lib = vm.library
    LaunchedEffect(Unit) { lib.loadMovies(); lib.loadSeries() }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(150); runCatching { first.requestFocus() } }
    var live by remember { mutableStateOf(if (vm.kidsLive.isNotEmpty()) vm.kidsLive else vm.suggestKids("Live")) }
    var movies by remember { mutableStateOf(vm.kidsMovies) }
    var series by remember { mutableStateOf(vm.kidsSeries) }
    var seeded by remember { mutableStateOf(vm.kidsMovies.isNotEmpty() || vm.kidsSeries.isNotEmpty()) }
    // Fill in suggestions for films and series once their lists arrive.
    LaunchedEffect(lib.movieCategories, lib.seriesCategories) {
        if (!seeded && (lib.movieCategories.isNotEmpty() || lib.seriesCategories.isNotEmpty())) {
            movies = vm.suggestKids("Movies"); series = vm.suggestKids("Series"); seeded = true
        }
    }
    BackHandler { vm.navigate(Screen.Settings) }

    Column(
        Modifier.fillMaxSize().background(Palette.Background).verticalScroll(rememberScrollState()).padding(horizontal = 56.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("KIDS MODE", color = Palette.Text, fontSize = 30.sp, fontWeight = FontWeight.Bold, fontFamily = DisplayFamily, letterSpacing = 1.sp)
        Text("Only what you tick here will be visible: everything else in Live TV, the guide, films, series, search and Home is hidden, including anything your provider adds later. ShedTV has ticked the categories whose names look like children's content; check them and change anything you like. \"Family\" categories aren't ticked, because they often include 12 and 15 films.",
            color = Palette.TextSoft, fontSize = 14.sp)
        PickList("LIVE TV CATEGORIES", "${live.size} ticked", vm.allCategories, live, { id -> live = if (id in live) live - id else live + id }, Modifier.focusRequester(first))
        Spacer(Modifier.height(4.dp))
        PickList("FILM CATEGORIES", "${movies.size} ticked", lib.movieCategories, movies, { id -> movies = if (id in movies) movies - id else movies + id })
        Spacer(Modifier.height(4.dp))
        PickList("SERIES CATEGORIES", "${series.size} ticked", lib.seriesCategories, series, { id -> series = if (id in series) series - id else series + id })
        Spacer(Modifier.height(6.dp))
        KidsExtras(vm)
        MakeItStick()
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Chip(if (vm.kidsMode) "Save changes" else "Start Kids mode", selected = true) {
                if (live.isEmpty() && movies.isEmpty() && series.isEmpty()) vm.flash = "Tick at least one category."
                else vm.startKids(live, movies, series)
            }
            Chip("Cancel") { vm.navigate(Screen.Settings) }
        }
        Text("Settings, subscriptions and turning Kids mode off will need your PIN.", color = Palette.TextMuted, fontSize = 12.sp)
    }
}

/** Bedtime, daily limit and the simpler Home. */
@Composable
fun KidsExtras(vm: AppViewModel) {
    Text("BEDTIME", color = Palette.Accent, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = DisplayFamily, letterSpacing = 1.sp)
    Text("ShedTV stops playing at this time until 6am. You can always allow another hour with your PIN.", color = Palette.TextMuted, fontSize = 12.sp)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(-1 to "No bedtime", 18 * 60 + 30 to "6:30pm", 19 * 60 to "7pm", 19 * 60 + 30 to "7:30pm", 20 * 60 to "8pm", 20 * 60 + 30 to "8:30pm", 21 * 60 to "9pm").forEach { (m, label) ->
            Chip(label, selected = vm.kidsBedtime == m) { vm.chooseKidsBedtime(m) }
        }
    }
    Text("DAILY LIMIT", color = Palette.Accent, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = DisplayFamily, letterSpacing = 1.sp)
    Text("Counts time spent watching (not browsing). Watched today: ${vm.kidsWatchedToday()} min.", color = Palette.TextMuted, fontSize = 12.sp)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(0 to "No limit", 60 to "1 hour", 90 to "1½ hours", 120 to "2 hours", 180 to "3 hours").forEach { (m, label) ->
            Chip(label, selected = vm.kidsLimit == m) { vm.chooseKidsLimit(m) }
        }
    }
    Text("HOME SCREEN", color = Palette.Accent, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = DisplayFamily, letterSpacing = 1.sp)
    Text("A simpler Home with big pictures of their channels and what they've been watching, instead of the watchlist.", color = Palette.TextMuted, fontSize = 12.sp)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip("Simple kids' Home", selected = vm.kidsSimpleHome) { vm.turnKidsSimpleHome(true) }
        Chip("Normal Home", selected = !vm.kidsSimpleHome) { vm.turnKidsSimpleHome(false) }
    }
}

/** The simple kids' Home: big tiles, nothing else. */
@Composable
fun KidsHome(vm: AppViewModel) {
    val now = rememberNow()
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(120); runCatching { first.requestFocus() } }
    BackHandler { vm.goHome() }
    val channels = vm.shownChannels.let { if (vm.mergeQualities) vm.merge(it) else it }.take(60)
    val cont = vm.continueWatching()
    var used = false
    fun firstMod(): Modifier = if (!used) { used = true; Modifier.focusRequester(first) } else Modifier

    Row(Modifier.fillMaxSize().background(Palette.Background)) {
        NavRail(vm, Screen.Home)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 22.dp, top = 24.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("  WHAT DO YOU WANT TO WATCH?", color = Palette.Text, fontSize = 28.sp, fontWeight = FontWeight.Bold, fontFamily = DisplayFamily, letterSpacing = 1.sp)
            if (cont.isNotEmpty()) {
                Text("  KEEP WATCHING", color = Palette.TextSoft, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = DisplayFamily, letterSpacing = 1.sp)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(start = 6.dp, end = 48.dp, top = 10.dp, bottom = 10.dp)) {
                    items(cont, key = { "k" + it.id }) { e ->
                        FocusTile(onClick = { vm.resume(e) }, corner = 18.dp, focusedScale = 1.06f, modifier = firstMod().width(280.dp)) {
                            Column {
                                Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Palette.CardRaised)) {
                                    if (e.image != null) AsyncImage(model = e.image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                }
                                Text(e.title, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(12.dp))
                            }
                        }
                    }
                }
            }
            Text("  CHANNELS", color = Palette.TextSoft, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = DisplayFamily, letterSpacing = 1.sp)
            if (channels.isEmpty()) Text("  No channels are allowed yet. A grown-up can choose some in Settings → Kids mode.", color = Palette.TextMuted, fontSize = 15.sp)
            FlowRow(Modifier.padding(start = 6.dp, end = 48.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                channels.forEach { ch ->
                    val prog = vm.programmesFor(ch).firstOrNull { it.isOn(now) }
                    FocusTile(onClick = { vm.play(ch, from = Screen.Home) }, corner = 18.dp, focusedScale = 1.06f, modifier = firstMod().width(230.dp)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ChannelLogo(ch, 72)
                            Text(vm.channelLabel(ch), fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(prog?.title ?: " ", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = LocalContentColor.current.copy(alpha = 0.7f))
                        }
                    }
                }
            }
        }
    }
}

/** Bedtime or the daily limit. */
@Composable
fun KidsStopOverlay(vm: AppViewModel, reason: String) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(reason) { delay(80); runCatching { focus.requestFocus() } }
    BackHandler { }
    Box(Modifier.fillMaxSize().background(Color(0xF207090C)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("🌙", fontSize = 64.sp)
            Text(reason, color = Palette.Text, fontSize = 30.sp, fontWeight = FontWeight.Bold, fontFamily = DisplayFamily)
            Text("See you tomorrow!", color = Palette.TextSoft, fontSize = 18.sp)
            Chip("Grown-ups: allow another hour", modifier = Modifier.focusRequester(focus)) { vm.kidsOneMoreHour() }
        }
    }
}

/** A new ShedTV has downloaded in the background: offer it, without interrupting anything. */
@Composable
fun UpdateOffer(vm: AppViewModel) {
    val build = vm.updater.readyBuild ?: return
    val focus = remember { FocusRequester() }
    LaunchedEffect(build) { delay(120); runCatching { focus.requestFocus() } }
    BackHandler { vm.updateLater = true }
    Box(Modifier.fillMaxSize().background(Color(0xCC07090C)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(520.dp).background(Palette.Card, androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("UPDATE READY", color = Palette.Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Text("ShedTV build $build", color = Palette.Text, fontSize = 26.sp, fontWeight = FontWeight.Bold, fontFamily = DisplayFamily)
            Text(
                "It's already downloaded. Installing takes a few seconds: your device asks you to confirm, then choose Open to carry on. " +
                    "Your settings, subscriptions and favourites stay as they are.",
                color = Palette.TextSoft, fontSize = 15.sp, lineHeight = 21.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                Chip("Install now", selected = true, modifier = Modifier.focusRequester(focus)) { vm.updateLater = true; vm.updater.install() }
                Chip("Later") { vm.updateLater = true }
            }
            Text("Turn this off in Settings → About & updates.", color = Palette.TextMuted, fontSize = 12.sp)
        }
    }
}
