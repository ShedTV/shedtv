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
import com.matchday.tv.data.Subscription
import com.matchday.tv.data.UserAgents
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
    data object Settings : Screen
    data object EditSubscription : Screen
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
    /** All saved provider logins, and the one in use. */
    var subscriptions by mutableStateOf(prefs.subscriptions)
        private set
    var activeSubscription by mutableStateOf(prefs.active)
        private set
    /** Subscription being edited in the form; null when adding a new one. */
    var editingSubscription by mutableStateOf<Subscription?>(null)
        private set

    var favourites by mutableStateOf(activeSubscription?.let { prefs.favourites(it.id) } ?: emptySet())
        private set
    val streamFormat: String get() = activeSubscription?.streamFormat ?: "ts"

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
    val library = Library(app, viewModelScope, { client }, { activeSubscription?.id ?: "none" })

    /** Where Back from the player returns to. */
    private var returnScreen: Screen = Screen.Live

    var playlist by mutableStateOf<List<Channel>>(emptyList())
        private set
    var playingIndex by mutableIntStateOf(0)
        private set
    private var previousIndex: Int? = null


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
        activeSubscription?.let { connect(it, attempts = 4) }
        // Keep fixtures, alerts and the guide fresh while the app is open.
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(60_000)
                if (client == null) continue
                if (guideLoadedAt > 0 && System.currentTimeMillis() - guideLoadedAt > 6 * 60 * 60 * 1000L) loadGuide(force = true)
                runCatching { sport.refresh(allChannels, guide, favourites) }
            }
        }
    }

    private var guideLoadedAt = 0L

    /** This channel plus other streams carrying the same thing (same guide id), best first. */
    fun sourcesFor(channel: Channel): List<Channel> {
        val id = channel.epgId?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return listOf(channel)
        val others = allChannels
            .filter { it.streamId != channel.streamId && it.epgId?.trim()?.lowercase() == id && !it.name.contains("+1") }
            .sortedByDescending { com.matchday.tv.data.SportMatcher.channelScore(it, favourites) }
        return (listOf(channel) + others).take(6)
    }

    /** Go straight to a channel from anywhere (kick-off alerts). */
    fun jumpToChannel(channel: Channel) {
        val from = if (screen == Screen.Player || screen == Screen.VodPlayer) returnScreen else screen
        play(channel, from)
    }

    /** Save a new or edited subscription from the form, then connect to it. */
    fun saveSubscription(name: String, credentials: Credentials) {
        val editing = editingSubscription
        val sub = (editing ?: Subscription(id = Prefs.newId(), name = "", server = "", username = "", password = "")).copy(
            name = name.ifBlank { editing?.name ?: "Subscription ${subscriptions.size + 1}" },
            server = XtreamClient.normaliseServer(credentials.server),
            username = credentials.username,
            password = credentials.password,
        )
        connect(sub, saveOnSuccess = true)
    }

    fun startAddSubscription() {
        editingSubscription = null
        error = null
        screen = Screen.EditSubscription
    }

    fun startEditSubscription(sub: Subscription) {
        editingSubscription = sub
        error = null
        screen = Screen.EditSubscription
    }

    fun cancelEdit() {
        error = null
        editingSubscription = null
        screen = if (client != null) Screen.Settings else Screen.Login
    }

    fun switchSubscription(sub: Subscription) {
        if (sub.id == activeSubscription?.id && client != null) return
        connect(sub, saveOnSuccess = false)
    }

    fun removeSubscription(sub: Subscription) {
        subscriptions = subscriptions.filterNot { it.id == sub.id }
        prefs.subscriptions = subscriptions
        if (sub.id == activeSubscription?.id) {
            clearSession()
            activeSubscription = null
            prefs.activeId = null
            val next = subscriptions.firstOrNull()
            if (next != null) connect(next) else screen = Screen.Login
        }
    }

    /** Change the user agent or stream format of the active subscription. */
    fun updateActive(userAgent: String? = null, streamFormat: String? = null) {
        val sub = activeSubscription ?: return
        val updated = sub.copy(
            userAgent = userAgent?.trim()?.ifBlank { null } ?: sub.userAgent,
            streamFormat = streamFormat ?: sub.streamFormat,
        )
        persist(updated)
        activeSubscription = updated
        UserAgents.current = updated.userAgent
    }

    private fun persist(sub: Subscription) {
        subscriptions = if (subscriptions.any { it.id == sub.id }) subscriptions.map { if (it.id == sub.id) sub else it } else subscriptions + sub
        prefs.subscriptions = subscriptions
    }

    private fun clearSession() {
        client = null
        allChannels = emptyList()
        categories = emptyList()
        epg.clear()
        guideJob?.cancel()
        guide = emptyMap()
        guideStatus = null
        library.reset()
        guideLoadedAt = 0
        playlist = emptyList()
        lastFocusedChannelId = null
    }

    private fun connect(sub: Subscription, saveOnSuccess: Boolean = false, attempts: Int = 1) {
        error = null
        val previous = activeSubscription
        val hadSession = client != null
        screen = Screen.Loading
        viewModelScope.launch {
            try {
                UserAgents.current = sub.userAgent
                val c = XtreamClient(sub.credentials)
                loadingMessage = "Signing in to ${sub.name}…"
                var attempt = 1
                while (true) {
                    try {
                        c.login()
                        break
                    } catch (e: Exception) {
                        // Right after the Fire Stick wakes, the network may not be up yet.
                        val network = e is java.net.UnknownHostException || e is java.net.ConnectException || e is java.net.SocketTimeoutException
                        if (!network || attempt >= attempts) throw e
                        attempt++
                        loadingMessage = "Waiting for the network…"
                        kotlinx.coroutines.delay(4_000)
                    }
                }
                clearSession()
                if (saveOnSuccess) persist(sub)
                activeSubscription = sub
                prefs.activeId = sub.id
                favourites = prefs.favourites(sub.id)
                editingSubscription = null
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
                error = "${sub.name}: " + (e.message ?: "couldn't connect to the provider.")
                UserAgents.current = previous?.userAgent ?: UserAgents.DEFAULT
                screen = when {
                    saveOnSuccess -> Screen.EditSubscription
                    hadSession || subscriptions.size > 1 -> Screen.Settings
                    else -> {
                        editingSubscription = sub
                        Screen.EditSubscription
                    }
                }
            }
        }
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
        activeSubscription?.let { prefs.setFavourites(it.id, favourites) }
    }

    fun toggleStreamFormat() {
        updateActive(streamFormat = if (streamFormat == "ts") "m3u8" else "ts")
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
        guideLoadedAt = System.currentTimeMillis()
        val ids = allChannels.mapNotNullTo(HashSet()) { it.epgId?.trim()?.lowercase()?.takeIf { id -> id.isNotEmpty() } }
        guideJob = viewModelScope.launch {
            try {
                val data = epgRepository.load(c, activeSubscription?.id ?: "none", ids, force) { msg ->
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
