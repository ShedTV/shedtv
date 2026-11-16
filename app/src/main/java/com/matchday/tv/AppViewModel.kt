package com.matchday.tv

import android.app.Application
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.matchday.tv.data.Category
import com.matchday.tv.data.Channel
import com.matchday.tv.data.Credentials
import com.matchday.tv.data.EpgEntry
import com.matchday.tv.data.EpgRepository
import com.matchday.tv.data.Prefs
import com.matchday.tv.data.XtreamClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Screen {
    data object Login : Screen
    data object Loading : Screen
    data object Sport : Screen
    data object Teams : Screen
    data object Live : Screen
    data object Guide : Screen
    data object Movies : Screen
    data object Series : Screen
    data object MovieDetails : Screen
    data object SeriesDetails : Screen
    data object VodPlayer : Screen
    data object Player : Screen
}

const val CATEGORY_FAVOURITES = "__fav"
const val CATEGORY_ALL = "__all"

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = Prefs(app)
    private var client: XtreamClient? = null

    var screen by mutableStateOf<Screen>(Screen.Login)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var loadingMessage by mutableStateOf("")
        private set

    var categories by mutableStateOf<List<Category>>(emptyList())
        private set
    private var allChannels by mutableStateOf<List<Channel>>(emptyList())
    var selectedCategoryId by mutableStateOf(CATEGORY_ALL)
        private set
    var favourites by mutableStateOf(prefs.favourites)
        private set
    var streamFormat by mutableStateOf(prefs.streamFormat)
        private set

    /** Channel that had focus in the list, so focus returns there after watching. */
    var lastFocusedChannelId by mutableStateOf<String?>(null)

    /** Short EPG per stream id. */
    val epg = mutableStateMapOf<String, List<EpgEntry>>()
    private val epgLoading = mutableSetOf<String>()

    /** Full XMLTV guide, keyed by lower-cased EPG channel id. */
    var guide by mutableStateOf<Map<String, List<EpgEntry>>>(emptyMap())
        private set
    /** Progress or problem message for the full guide; null when ready. */
    var guideStatus by mutableStateOf<String?>(null)
        private set
    private var guideJob: Job? = null
    private val epgRepository = EpgRepository(app)

    /** Followed teams and fixtures from the guide. */
    val sport = SportHub(app, viewModelScope)

    /** Movies and series. */
    val library = Library(app, viewModelScope) { client }

    /** Where Back from the player returns to. */
    private var returnScreen: Screen = Screen.Live

    var playlist by mutableStateOf<List<Channel>>(emptyList())
        private set
    var playingIndex by mutableIntStateOf(0)
        private set
    private var previousIndex: Int? = null

    val savedCredentials: Credentials? get() = prefs.credentials

    val visibleChannels: List<Channel> by derivedStateOf {
        when (selectedCategoryId) {
            CATEGORY_ALL -> allChannels
            CATEGORY_FAVOURITES -> allChannels.filter { it.streamId in favourites }
            else -> allChannels.filter { it.categoryId == selectedCategoryId }
        }
    }

    /** Categories shown in the side list, with the two built-in ones first. */
    val categoryList: List<Category> by derivedStateOf {
        listOf(
            Category(CATEGORY_FAVOURITES, "Favourites"),
            Category(CATEGORY_ALL, "All channels"),
        ) + categories
    }

    val currentChannel: Channel? get() = playlist.getOrNull(playingIndex)

    /** Every live channel from the provider. */
    val channels: List<Channel> get() = allChannels

    init {
        prefs.credentials?.let { connect(it, save = false) }
    }

    fun connect(credentials: Credentials, save: Boolean = true) {
        error = null
        screen = Screen.Loading
        viewModelScope.launch {
            try {
                val c = XtreamClient(credentials)
                loadingMessage = "Signing in…"
                c.login()
                if (save) prefs.credentials = c.creds
                loadingMessage = "Loading channels…"
                val cats = c.liveCategories()
                val chans = c.liveStreams()
                if (chans.isEmpty()) throw IllegalStateException("No live channels found on this account.")
                client = c
                categories = cats
                allChannels = chans
                // Drop favourites that no longer exist on the provider.
                val ids = chans.mapTo(HashSet()) { it.streamId }
                favourites = favourites.filterTo(HashSet()) { it in ids }
                selectedCategoryId = if (favourites.isNotEmpty()) CATEGORY_FAVOURITES else CATEGORY_ALL
                screen = Screen.Sport
                loadGuide()
            } catch (e: Exception) {
                error = e.message ?: "Couldn't connect to the provider."
                screen = Screen.Login
            }
        }
    }

    fun signOut() {
        prefs.credentials = null
        client = null
        allChannels = emptyList()
        categories = emptyList()
        epg.clear()
        guideJob?.cancel()
        guide = emptyMap()
        guideStatus = null
        library.reset()
        screen = Screen.Login
    }

    fun selectCategory(id: String) {
        selectedCategoryId = id
    }

    fun channelCount(categoryId: String): Int = when (categoryId) {
        CATEGORY_ALL -> allChannels.size
        CATEGORY_FAVOURITES -> favourites.size
        else -> allChannels.count { it.categoryId == categoryId }
    }

    fun isFavourite(channel: Channel) = channel.streamId in favourites

    fun toggleFavourite(channel: Channel) {
        favourites = if (isFavourite(channel)) favourites - channel.streamId else favourites + channel.streamId
        prefs.favourites = favourites
    }

    fun toggleStreamFormat() {
        streamFormat = if (streamFormat == "ts") "m3u8" else "ts"
        prefs.streamFormat = streamFormat
    }

    fun loadEpg(channel: Channel, force: Boolean = false) {
        if (!force && programmesFor(channel).isNotEmpty()) return
        val id = channel.streamId
        if (!force && (epg.containsKey(id) || id in epgLoading)) return
        val c = client ?: return
        epgLoading += id
        viewModelScope.launch {
            try {
                epg[id] = c.shortEpg(id)
            } catch (_: Exception) {
                // No guide data for this channel; leave it empty.
            } finally {
                epgLoading -= id
            }
        }
    }

    fun navigate(to: Screen) {
        screen = to
    }

    /** Step through categories (Menu button in the guide). */
    fun cycleCategory(delta: Int = 1) {
        val list = categoryList.filter { it.id == selectedCategoryId || channelCount(it.id) > 0 }
        if (list.isEmpty()) return
        val idx = list.indexOfFirst { it.id == selectedCategoryId }.coerceAtLeast(0)
        selectedCategoryId = list[(idx + delta).mod(list.size)].id
    }

    /** If the current category is empty (e.g. no favourites yet), fall back to all channels. */
    fun ensureCategoryHasChannels() {
        if (allChannels.isNotEmpty() && visibleChannels.isEmpty()) selectedCategoryId = CATEGORY_ALL
    }

    /** Programmes for a channel: the full guide if we have it, else the short EPG. */
    fun programmesFor(channel: Channel): List<EpgEntry> {
        val full = channel.epgId?.trim()?.lowercase()?.let { guide[it] }
        return if (!full.isNullOrEmpty()) full else epg[channel.streamId].orEmpty()
    }

    fun loadGuide(force: Boolean = false) {
        val c = client ?: return
        if (guideJob?.isActive == true) return
        val ids = allChannels.mapNotNullTo(HashSet()) { it.epgId?.trim()?.lowercase()?.takeIf { id -> id.isNotEmpty() } }
        guideJob = viewModelScope.launch {
            try {
                val data = epgRepository.load(c, ids, force) { msg ->
                    withContext(Dispatchers.Main) { guideStatus = msg }
                }
                guide = data
                guideStatus = if (data.isEmpty()) "Your provider's guide had no listings for these channels." else null
            } catch (e: Exception) {
                guideStatus = "Couldn't load the TV guide (${e.message ?: "unknown error"})."
            }
        }
    }

    fun playVod(p: com.matchday.tv.data.Playable) {
        library.playing = p
        returnScreen = screen
        screen = Screen.VodPlayer
    }

    fun play(channel: Channel, from: Screen = Screen.Live) {
        returnScreen = from
        // From the Sport Hub the channel may not be in the current category: zap through everything.
        val list = visibleChannels.takeIf { l -> l.any { it.streamId == channel.streamId } } ?: allChannels
        playlist = list
        playingIndex = list.indexOfFirst { it.streamId == channel.streamId }.coerceAtLeast(0)
        previousIndex = null
        lastFocusedChannelId = channel.streamId
        screen = Screen.Player
    }

    /** Move up or down the channel list while watching. */
    fun zap(delta: Int) {
        if (playlist.isEmpty()) return
        previousIndex = playingIndex
        playingIndex = (playingIndex + delta).mod(playlist.size)
        lastFocusedChannelId = currentChannel?.streamId
    }

    /** Jump back to the channel watched before the last switch. */
    fun lastChannel() {
        val prev = previousIndex ?: return
        previousIndex = playingIndex
        playingIndex = prev
        lastFocusedChannelId = currentChannel?.streamId
    }

    fun streamUrl(channel: Channel): String? = client?.streamUrl(channel, streamFormat)

    fun closePlayer() {
        screen = returnScreen
    }
}
