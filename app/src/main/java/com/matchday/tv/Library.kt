package com.matchday.tv

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.matchday.tv.data.Category
import com.matchday.tv.data.Episode
import com.matchday.tv.data.MovieInfo
import com.matchday.tv.data.Playable
import com.matchday.tv.data.SeriesInfo
import com.matchday.tv.data.SeriesItem
import com.matchday.tv.data.VodItem
import com.matchday.tv.data.WatchEntry
import com.matchday.tv.data.WatchHistory
import com.matchday.tv.data.XtreamClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

const val CAT_CONTINUE = "__continue"
const val CAT_RECENT = "__recent"
const val CAT_ALL_VOD = "__allvod"

/** Movies and series: catalogue, details and resume points. */
class Library(
    app: Application,
    private val scope: CoroutineScope,
    private val client: () -> XtreamClient?,
    subscriptionId: () -> String,
) {
    private val history = WatchHistory(app, subscriptionId)

    /** Bumped whenever resume points change, so screens re-read them. */
    var historyVersion by mutableIntStateOf(0)
        private set

    // Movies
    var movieCategories by mutableStateOf<List<Category>>(emptyList())
        private set
    var movies by mutableStateOf<List<VodItem>>(emptyList())
        private set
    var moviesStatus by mutableStateOf<String?>(null)
        private set
    var movieCategory by mutableStateOf(CAT_RECENT)
    private var moviesJob: Job? = null

    // Series
    var seriesCategories by mutableStateOf<List<Category>>(emptyList())
        private set
    var series by mutableStateOf<List<SeriesItem>>(emptyList())
        private set
    var seriesStatus by mutableStateOf<String?>(null)
        private set
    var seriesCategory by mutableStateOf(CAT_RECENT)
    private var seriesJob: Job? = null

    // Details
    var openMovie by mutableStateOf<VodItem?>(null)
        private set
    var movieInfo by mutableStateOf<MovieInfo?>(null)
        private set
    var openSeries by mutableStateOf<SeriesItem?>(null)
        private set
    var seriesInfo by mutableStateOf<SeriesInfo?>(null)
        private set
    var detailStatus by mutableStateOf<String?>(null)
        private set
    var selectedSeason by mutableIntStateOf(1)

    /** What the on-demand player is playing. */
    var playing by mutableStateOf<Playable?>(null)

    fun reset() {
        moviesJob?.cancel(); seriesJob?.cancel()
        movies = emptyList(); series = emptyList()
        movieCategories = emptyList(); seriesCategories = emptyList()
        moviesStatus = null; seriesStatus = null
    }

    fun loadMovies() {
        val c = client() ?: return
        if (movies.isNotEmpty() || moviesJob?.isActive == true) return
        moviesJob = scope.launch {
            moviesStatus = "Loading movies…"
            try {
                movieCategories = c.vodCategories()
                movies = c.vodStreams()
                movieCategory = if (history().any { it.kind == "movie" }) CAT_CONTINUE else CAT_RECENT
                moviesStatus = if (movies.isEmpty()) "Your provider has no movies on this account." else null
            } catch (e: Exception) {
                moviesStatus = "Couldn't load movies (${e.message})."
            }
        }
    }

    fun loadSeries() {
        val c = client() ?: return
        if (series.isNotEmpty() || seriesJob?.isActive == true) return
        seriesJob = scope.launch {
            seriesStatus = "Loading series…"
            try {
                seriesCategories = c.seriesCategories()
                series = c.series()
                seriesCategory = if (history().any { it.kind == "episode" }) CAT_CONTINUE else CAT_RECENT
                seriesStatus = if (series.isEmpty()) "Your provider has no series on this account." else null
            } catch (e: Exception) {
                seriesStatus = "Couldn't load series (${e.message})."
            }
        }
    }

    fun history(): List<WatchEntry> = history.all()

    fun movieCategoryList() = listOf(
        Category(CAT_CONTINUE, "Continue watching"),
        Category(CAT_RECENT, "Recently added"),
        Category(CAT_ALL_VOD, "All movies"),
    ) + movieCategories

    fun seriesCategoryList() = listOf(
        Category(CAT_CONTINUE, "Continue watching"),
        Category(CAT_RECENT, "Recently updated"),
        Category(CAT_ALL_VOD, "All series"),
    ) + seriesCategories

    fun moviesIn(categoryId: String): List<VodItem> = when (categoryId) {
        CAT_CONTINUE -> {
            val byId = movies.associateBy { it.id }
            history().filter { it.kind == "movie" }.mapNotNull { byId[it.id] }
        }
        CAT_RECENT -> movies.sortedByDescending { it.added }.take(200)
        CAT_ALL_VOD -> movies
        else -> movies.filter { it.categoryId == categoryId }
    }

    fun seriesIn(categoryId: String): List<SeriesItem> = when (categoryId) {
        CAT_CONTINUE -> {
            val byId = series.associateBy { it.id }
            history().filter { it.kind == "episode" }.mapNotNull { it.parentId }.distinct().mapNotNull { byId[it] }
        }
        CAT_RECENT -> series.sortedByDescending { it.updated }.take(200)
        CAT_ALL_VOD -> series
        else -> series.filter { it.categoryId == categoryId }
    }

    fun showMovie(item: VodItem) {
        openMovie = item
        movieInfo = null
        detailStatus = "Loading…"
        val c = client() ?: return
        scope.launch {
            try {
                movieInfo = c.vodInfo(item.id)
                detailStatus = null
            } catch (e: Exception) {
                detailStatus = null // details are optional; the film can still play
            }
        }
    }

    fun showSeries(item: SeriesItem) {
        openSeries = item
        seriesInfo = null
        detailStatus = "Loading episodes…"
        val c = client() ?: return
        scope.launch {
            try {
                val info = c.seriesInfo(item.id)
                seriesInfo = info
                // Open on the season of the last episode watched, else the first.
                val last = history().firstOrNull { it.kind == "episode" && it.parentId == item.id }
                val lastSeason = last?.let { h -> info.seasons.entries.firstOrNull { (_, eps) -> eps.any { it.id == h.id } }?.key }
                selectedSeason = lastSeason ?: info.seasons.keys.firstOrNull() ?: 1
                detailStatus = if (info.seasons.isEmpty()) "No episodes available." else null
            } catch (e: Exception) {
                detailStatus = "Couldn't load episodes (${e.message})."
            }
        }
    }

    fun resumePoint(kind: String, id: String): WatchEntry? {
        historyVersion // read so callers recompose when it changes
        return history.get(kind, id)
    }

    fun moviePlayable(item: VodItem, fromStart: Boolean): Playable? {
        val c = client() ?: return null
        val ext = movieInfo?.takeIf { openMovie?.id == item.id }?.ext?.ifBlank { null } ?: item.ext
        return Playable(
            kind = "movie", id = item.id, title = item.name,
            subtitle = movieInfo?.year.orEmpty(),
            url = c.movieUrl(item.id, ext), image = item.poster, parentId = null,
            startAt = if (fromStart) 0 else history.get("movie", item.id)?.position ?: 0,
        )
    }

    fun episodePlayable(show: SeriesItem, episodes: List<Episode>, index: Int, fromStart: Boolean): Playable? {
        val c = client() ?: return null
        fun make(ep: Episode, start: Long) = Playable(
            kind = "episode", id = ep.id,
            title = show.name,
            subtitle = "S${ep.season} E${ep.num}" + (ep.title.takeIf { it.isNotBlank() && !it.contains(show.name, true) }?.let { " · $it" } ?: ""),
            url = c.episodeUrl(ep.id, ep.ext), image = show.cover, parentId = show.id, startAt = start,
        )
        val ep = episodes.getOrNull(index) ?: return null
        val start = if (fromStart) 0 else history.get("episode", ep.id)?.position ?: 0
        return make(ep, start).copy(upNext = episodes.drop(index + 1).map { make(it, 0) })
    }

    /** Called by the player as it goes; finished items drop out of Continue watching. */
    fun savePosition(p: Playable, position: Long, duration: Long) {
        if (duration <= 0) return
        val finished = position > duration * 0.95 || duration - position < 120_000
        if (finished) {
            history.remove(p.kind, p.id)
        } else if (position > 30_000) {
            history.save(
                WatchEntry(p.kind, p.id, p.title, p.subtitle, p.image, p.parentId, position, duration, System.currentTimeMillis())
            )
        }
        historyVersion++
    }
}
