@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.matchday.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
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

/** One poster tile's data, shared by movies and series. */
private data class PosterData(val id: String, val title: String, val image: String?, val caption: String, val progress: Float?)

@Composable
fun MoviesScreen(vm: AppViewModel) {
    val lib = vm.library
    LaunchedEffect(Unit) { lib.loadMovies() }
    val cat = lib.movieCategory
    val items = remember(lib.movies, cat, lib.historyVersion) { lib.moviesIn(cat) }
    val history = remember(lib.historyVersion) { lib.history().filter { it.kind == "movie" }.associateBy { it.id } }
    BrowseLayout(
        vm = vm, current = Screen.Movies, title = "MOVIES",
        status = lib.moviesStatus,
        categories = lib.movieCategoryList(), selected = cat, onCategory = { lib.movieCategory = it },
        count = { id -> if (id == cat) items.size else null },
        posters = remember(items, history) { items.map { PosterData(it.id, it.name, it.poster, it.rating?.let { r -> "★ $r" }.orEmpty(), history[it.id]?.progress) } },
        onOpen = { id ->
            items.firstOrNull { it.id == id }?.let { lib.showMovie(it); vm.navigate(Screen.MovieDetails) }
        },
    )
}

@Composable
fun SeriesScreen(vm: AppViewModel) {
    val lib = vm.library
    LaunchedEffect(Unit) { lib.loadSeries() }
    val cat = lib.seriesCategory
    val items = remember(lib.series, cat, lib.historyVersion) { lib.seriesIn(cat) }
    BrowseLayout(
        vm = vm, current = Screen.Series, title = "SERIES",
        status = lib.seriesStatus,
        categories = lib.seriesCategoryList(), selected = cat, onCategory = { lib.seriesCategory = it },
        count = { id -> if (id == cat) items.size else null },
        posters = remember(items) { items.map { PosterData(it.id, it.name, it.cover, listOf(it.year, it.genre.substringBefore(",")).filter { s -> s.isNotBlank() }.joinToString(" · "), null) } },
        onOpen = { id ->
            items.firstOrNull { it.id == id }?.let { lib.showSeries(it); vm.navigate(Screen.SeriesDetails) }
        },
    )
}

@Composable
private fun BrowseLayout(
    vm: AppViewModel,
    current: Screen,
    title: String,
    status: String?,
    categories: List<Category>,
    selected: String,
    onCategory: (String) -> Unit,
    count: (String) -> Int?,
    posters: List<PosterData>,
    onOpen: (String) -> Unit,
) {
    val firstCategory = remember { FocusRequester() }
    LaunchedEffect(categories.size) {
        delay(80)
        runCatching { firstCategory.requestFocus() }
    }
    BackHandler { vm.navigate(Screen.Sport) }

    Row(Modifier.fillMaxSize().background(Palette.Background)) {
        NavRail(vm, current)
        Row(
            Modifier.fillMaxSize().padding(start = 20.dp, top = 28.dp, end = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(Modifier.width(200.dp).fillMaxHeight()) {
                Text(title, color = Palette.Text, fontWeight = FontWeight.Bold, fontFamily = DisplayFamily, fontSize = 24.sp, letterSpacing = 1.sp,
                    modifier = Modifier.padding(start = 6.dp, top = 2.dp))
                Spacer(Modifier.height(14.dp))
                LazyColumn(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(vertical = 6.dp, horizontal = 4.dp),
                ) {
                    itemsIndexed(categories, key = { _, c -> c.id }) { index, c ->
                        val isSel = c.id == selected
                        FocusTile(
                            onClick = { onCategory(c.id) },
                            selected = isSel,
                            corner = 10.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (index == categories.indexOfFirst { it.id == selected }.coerceAtLeast(0)) Modifier.focusRequester(firstCategory) else Modifier)
                                .onFocusChanged { if (it.isFocused) onCategory(c.id) },
                        ) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(c.name, fontSize = 15.sp, fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                count(c.id)?.let { Text(it.toString(), fontSize = 12.sp, color = LocalContentColor.current.copy(alpha = 0.6f)) }
                            }
                        }
                    }
                }
            }

            Box(Modifier.weight(1f).fillMaxHeight()) {
                when {
                    status != null && posters.isEmpty() -> Text(status, color = Palette.TextMuted, fontSize = 17.sp, modifier = Modifier.padding(top = 50.dp))
                    posters.isEmpty() -> Text(
                        if (selected == com.matchday.tv.CAT_CONTINUE) "Nothing in progress yet. Anything you start watching appears here."
                        else "Nothing in this category.",
                        color = Palette.TextMuted, fontSize = 16.sp, modifier = Modifier.padding(top = 50.dp),
                    )
                    else -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(118.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 42.dp, bottom = 28.dp, start = 6.dp, end = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(posters, key = { it.id }) { p -> PosterTile(p) { onOpen(p.id) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun PosterTile(p: PosterData, onClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FocusTile(onClick = onClick, corner = 10.dp, focusedScale = 1.06f, modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f)) {
            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)).background(Palette.Card)) {
                Text(
                    p.title, color = Palette.TextMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 4,
                    modifier = Modifier.align(Alignment.Center).padding(10.dp),
                )
                if (p.image != null) {
                    AsyncImage(model = p.image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                p.progress?.let {
                    ProgressBar(it, Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(8.dp), height = 4.dp, track = Color(0x66000000))
                }
            }
        }
        Text(p.title, color = Palette.Text, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (p.caption.isNotBlank()) Text(p.caption, color = Palette.TextMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ---------- Details ----------

@Composable
private fun DetailsBackdrop(image: String?, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Palette.Background)) {
        if (image != null) {
            AsyncImage(
                model = image, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(listOf(Palette.Background, Color(0xEE07090C), Color(0x9907090C), Color(0x5507090C)))
            )
        )
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, Palette.Background))))
        content()
    }
}

@Composable
private fun ActionButton(label: String, primary: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FocusTile(onClick = onClick, corner = 10.dp, focusedScale = 1.05f, modifier = modifier) {
        Text(
            label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .background(if (primary) Color(0x33FFFFFF) else Color(0x1AFFFFFF), RoundedCornerShape(10.dp))
                .padding(horizontal = 22.dp, vertical = 11.dp),
        )
    }
}

private fun fmtClock(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

@Composable
fun MovieDetailsScreen(vm: AppViewModel) {
    val lib = vm.library
    val movie = lib.openMovie ?: run { vm.navigate(Screen.Movies); return }
    val info = lib.movieInfo
    val resume = lib.resumePoint("movie", lib.historyId(movie.subId, movie.id))
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(movie.id) { delay(80); runCatching { playFocus.requestFocus() } }
    BackHandler { vm.navigate(Screen.Movies) }

    DetailsBackdrop(info?.backdrop ?: movie.poster) {
        Row(Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 48.dp), horizontalArrangement = Arrangement.spacedBy(36.dp)) {
            Box(Modifier.width(200.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(14.dp)).background(Palette.Card)) {
                if (movie.poster != null) AsyncImage(model = movie.poster, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(movie.name.uppercase(), color = Palette.Text, fontSize = 42.sp, fontWeight = FontWeight.Bold, fontFamily = DisplayFamily, lineHeight = 44.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val meta = listOfNotNull(
                    info?.year?.takeIf { it.isNotBlank() }, info?.genre?.takeIf { it.isNotBlank() },
                    info?.duration?.takeIf { it.isNotBlank() }, (info?.rating ?: movie.rating)?.let { "★ $it" },
                ).joinToString("  ·  ")
                if (meta.isNotBlank()) Text(meta, color = Palette.TextSoft, fontSize = 15.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                    if (resume != null) {
                        ActionButton("▶  Resume from ${fmtClock(resume.position)}", primary = true, modifier = Modifier.focusRequester(playFocus)) {
                            lib.moviePlayable(movie, fromStart = false)?.let { vm.playVod(it) }
                        }
                        ActionButton("Start over", primary = false) {
                            lib.moviePlayable(movie, fromStart = true)?.let { vm.playVod(it) }
                        }
                    } else {
                        ActionButton("▶  Play", primary = true, modifier = Modifier.focusRequester(playFocus)) {
                            lib.moviePlayable(movie, fromStart = true)?.let { vm.playVod(it) }
                        }
                    }
                }
                lib.detailStatus?.let { Text(it, color = Palette.TextMuted, fontSize = 14.sp) }
                info?.plot?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = Palette.TextSoft, fontSize = 15.sp, lineHeight = 22.sp, maxLines = 6, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(620.dp))
                }
                info?.director?.takeIf { it.isNotBlank() }?.let { Text("Director: $it", color = Palette.TextMuted, fontSize = 13.sp, maxLines = 1) }
                info?.cast?.takeIf { it.isNotBlank() }?.let { Text("Cast: $it", color = Palette.TextMuted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(620.dp)) }
            }
        }
    }
}

@Composable
fun SeriesDetailsScreen(vm: AppViewModel) {
    val lib = vm.library
    val show = lib.openSeries ?: run { vm.navigate(Screen.Series); return }
    val info = lib.seriesInfo
    val seasons = info?.seasons.orEmpty()
    val episodes = seasons[lib.selectedSeason].orEmpty()
    val historyVersion = lib.historyVersion
    val watched = remember(historyVersion, show.id) { lib.history().filter { it.parentId == lib.historyId(show.subId, show.id) }.associateBy { it.id } }
    val lastWatched = remember(historyVersion, show.id) { lib.history().firstOrNull { it.kind == "episode" && it.parentId == lib.historyId(show.subId, show.id) } }
    val firstFocus = remember { FocusRequester() }
    var focusedOnce by remember(show.id) { mutableStateOf(false) }
    LaunchedEffect(info) {
        if (info != null && !focusedOnce) { delay(100); runCatching { firstFocus.requestFocus() }; focusedOnce = true }
    }
    BackHandler { vm.navigate(Screen.Series) }

    DetailsBackdrop(info?.backdrop ?: show.cover) {
        Column(Modifier.fillMaxSize().padding(start = 56.dp, end = 40.dp, top = 40.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(show.name.uppercase(), color = Palette.Text, fontSize = 40.sp, fontWeight = FontWeight.Bold, fontFamily = DisplayFamily, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val meta = listOf(info?.year ?: show.year, info?.genre ?: show.genre, show.rating?.let { "★ $it" }.orEmpty(),
                if (seasons.isNotEmpty()) "${seasons.size} season" + (if (seasons.size == 1) "" else "s") else "")
                .filter { it.isNotBlank() }.joinToString("  ·  ")
            if (meta.isNotBlank()) Text(meta, color = Palette.TextSoft, fontSize = 14.sp)
            (info?.plot?.ifBlank { null } ?: show.plot.ifBlank { null })?.let {
                Text(it, color = Palette.TextSoft, fontSize = 14.sp, lineHeight = 20.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(640.dp))
            }
            lib.detailStatus?.let { Text(it, color = Palette.TextMuted, fontSize = 14.sp) }

            if (lastWatched != null && info != null) {
                val (season, list) = seasons.entries.firstOrNull { (_, eps) -> eps.any { lib.historyId(show.subId, it.id) == lastWatched.id } }?.toPair() ?: (null to null)
                if (season != null && list != null) {
                    val idx = list.indexOfFirst { lib.historyId(show.subId, it.id) == lastWatched.id }
                    ActionButton("▶  Continue ${lastWatched.subtitle.substringBefore(" · ")} from ${fmtClock(lastWatched.position)}", primary = true,
                        modifier = Modifier.padding(top = 4.dp).focusRequester(firstFocus)) {
                        lib.episodePlayable(show, list, idx, fromStart = false)?.let { vm.playVod(it) }
                    }
                }
            }

            if (seasons.size > 1) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 6.dp, horizontal = 2.dp)) {
                    items(seasons.keys.toList(), key = { it }) { s ->
                        FocusTile(
                            onClick = { lib.selectedSeason = s }, selected = s == lib.selectedSeason, corner = 8.dp,
                            modifier = Modifier.onFocusChanged { if (it.isFocused) lib.selectedSeason = s },
                        ) {
                            Text("Season $s", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                        }
                    }
                }
            }

            LazyColumn(
                Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp, end = 8.dp, start = 2.dp),
            ) {
                itemsIndexed(episodes, key = { _, e -> e.id }) { index, ep ->
                    val w = watched[lib.historyId(show.subId, ep.id)]
                    FocusTile(
                        onClick = { lib.episodePlayable(show, episodes, index, fromStart = false)?.let { vm.playVod(it) } },
                        corner = 12.dp, focusedScale = 1.02f,
                        modifier = Modifier.width(720.dp).then(if (index == 0 && lastWatched == null) Modifier.focusRequester(firstFocus) else Modifier),
                    ) {
                        Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.width(128.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)).background(Palette.CardRaised)) {
                                Text("E${ep.num}", color = Palette.TextMuted, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Center))
                                if (ep.image != null) AsyncImage(model = ep.image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                w?.let { ProgressBar(it.progress, Modifier.align(Alignment.BottomCenter).fillMaxWidth(), height = 4.dp, track = Color(0x66000000)) }
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    "${ep.num}. " + ep.title.ifBlank { "Episode ${ep.num}" },
                                    fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                                val sub = listOfNotNull(ep.duration.takeIf { it.isNotBlank() }, w?.let { "Resume at ${fmtClock(it.position)}" }).joinToString("  ·  ")
                                if (sub.isNotBlank()) Text(sub, fontSize = 12.sp, color = LocalContentColor.current.copy(alpha = 0.7f))
                                if (ep.plot.isNotBlank()) Text(ep.plot, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, color = LocalContentColor.current.copy(alpha = 0.8f))
                            }
                        }
                    }
                }
            }
        }
    }
}
