@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.matchday.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.matchday.tv.AppViewModel
import com.matchday.tv.Screen
import com.matchday.tv.SearchResults
import com.matchday.tv.data.nowAndNext
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val searchDay = SimpleDateFormat("EEE HH:mm", Locale.UK)

@Composable
fun SearchScreen(vm: AppViewModel) {
    val lib = vm.library
    val now = rememberNow()
    var results by remember { mutableStateOf(SearchResults()) }
    val field = remember { FocusRequester() }
    val focus = LocalFocusManager.current

    LaunchedEffect(Unit) {
        lib.loadMovies()
        lib.loadSeries()
        if (vm.searchQuery.isBlank()) { delay(120); runCatching { field.requestFocus() } }
    }
    // Search as you type, after a short pause.
    LaunchedEffect(vm.searchQuery, lib.movies, lib.series, vm.guide) {
        delay(250)
        results = vm.search(vm.searchQuery)
    }
    BackHandler { vm.navigate(Screen.Sport) }

    Row(Modifier.fillMaxSize().background(Palette.Background)) {
        NavRail(vm, Screen.Search)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 36.dp, top = 24.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Text("SEARCH", color = Palette.Text, fontSize = 28.sp, fontWeight = FontWeight.Bold, fontFamily = DisplayFamily, letterSpacing = 1.sp)
                OutlinedTextField(
                    value = vm.searchQuery, onValueChange = { vm.searchQuery = it }, singleLine = true,
                    label = { androidx.compose.material3.Text("Channels, programmes, teams, movies, series") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, autoCorrectEnabled = false),
                    keyboardActions = KeyboardActions(onSearch = { focus.moveFocus(FocusDirection.Down) }),
                    modifier = Modifier.width(520.dp).focusRequester(field),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Palette.Text, unfocusedTextColor = Palette.Text,
                        focusedBorderColor = Palette.Accent, unfocusedBorderColor = Palette.TextMuted,
                        focusedLabelColor = Palette.Accent, unfocusedLabelColor = Palette.TextMuted, cursorColor = Palette.Accent,
                        focusedContainerColor = Palette.Card, unfocusedContainerColor = Palette.Card,
                    ),
                )
            }

            val q = vm.searchQuery.trim()
            when {
                q.length < 2 -> Text("Type at least two letters.", color = Palette.TextMuted, fontSize = 15.sp)
                results.isEmpty -> Text(
                    if (lib.moviesStatus != null || lib.seriesStatus != null) "Nothing yet. Movies and series are still loading…" else "Nothing found for \"$q\".",
                    color = Palette.TextMuted, fontSize = 15.sp,
                )
            }

            if (results.channels.isNotEmpty()) SearchSection("Channels", results.channels.size) {
                items(results.channels, key = { it.streamId }) { ch ->
                    val current = vm.programmesFor(ch).nowAndNext(now).first
                    FocusTile(onClick = { vm.play(ch, from = Screen.Search) }, onLongClick = { vm.toggleFavourite(ch) }, corner = 12.dp, focusedScale = 1.05f) {
                        Row(Modifier.width(250.dp).padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ChannelLogo(ch, 40)
                            Column(Modifier.weight(1f)) {
                                Text(ch.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(current?.title ?: "Channel ${ch.num}", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = LocalContentColor.current.copy(alpha = 0.7f))
                            }
                            if (vm.isFavourite(ch)) Text("★", fontSize = 14.sp, color = Palette.Accent)
                        }
                    }
                }
            }

            if (results.programmes.isNotEmpty()) SearchSection("On TV", results.programmes.size) {
                items(results.programmes, key = { it.first.streamId + it.second.start }) { (ch, p) ->
                    val on = p.isOn(now)
                    FocusTile(onClick = { vm.play(ch, from = Screen.Search) }, corner = 12.dp, focusedScale = 1.05f) {
                        Column(Modifier.width(270.dp).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                if (on) "ON NOW  ·  until ${formatTime(p.end)}" else searchDay.format(Date(p.start)).uppercase(),
                                fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
                                color = if (on) Palette.Live else LocalContentColor.current.copy(alpha = 0.7f),
                            )
                            Text(p.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(ch.name, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = LocalContentColor.current.copy(alpha = 0.7f))
                        }
                    }
                }
            }

            if (results.movies.isNotEmpty()) SearchSection("Movies", results.movies.size) {
                items(results.movies, key = { it.id }) { m ->
                    SearchPoster(m.name, m.poster) { lib.showMovie(m); vm.navigate(Screen.MovieDetails) }
                }
            }

            if (results.series.isNotEmpty()) SearchSection("Series", results.series.size) {
                items(results.series, key = { it.id }) { s ->
                    SearchPoster(s.name, s.cover) { lib.showSeries(s); vm.navigate(Screen.SeriesDetails) }
                }
            }
        }
    }
}

@Composable
private fun SearchSection(title: String, count: Int, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("$title  ·  $count", color = Palette.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        LazyRow(modifier = Modifier.fadeRightEdge(), horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 10.dp, horizontal = 16.dp), content = content)
    }
}

@Composable
private fun SearchPoster(title: String, image: String?, onClick: () -> Unit) {
    Column(Modifier.width(112.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FocusTile(onClick = onClick, corner = 10.dp, focusedScale = 1.06f, modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f)) {
            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)).background(Palette.Card)) {
                Text(title, color = Palette.TextMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 4, modifier = Modifier.align(Alignment.Center).padding(8.dp))
                if (image != null) AsyncImage(model = image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Text(title, color = Palette.Text, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
