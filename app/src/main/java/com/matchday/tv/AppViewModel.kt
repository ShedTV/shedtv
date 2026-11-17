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
    data object Search : Screen
    data object Categories : Screen
    data object EditSubscription : Screen
}

data class SearchResults(
    val channels: List<Channel> = emptyList(),
    val programmes: List<Pair<Channel, EpgEntry>> = emptyList(),
    val movies: List<com.matchday.tv.data.VodItem> = emptyList(),
    val series: List<com.matchday.tv.data.SeriesItem> = emptyList(),
) {
    val isEmpty get() = channels.isEmpty() && programmes.isEmpty() && movies.isEmpty() && series.isEmpty()
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

    /** In-app updates. */
    val updater = Updater(app, viewModelScope)

    /** Movies and series. */
    val library = Library(app, viewModelScope, { sub -> clientFor(sub) }, { sub -> agentFor(sub) }, { activeSubscription?.id ?: "none" })

    // ---------- Other subscriptions, as backups ----------

    /** What we know about a non-active subscription. */
    data class BackupSub(
        val channels: List<Channel> = emptyList(),
        val movies: List<com.matchday.tv.data.VodItem> = emptyList(),
        val series: List<com.matchday.tv.data.SeriesItem> = emptyList(),
        val status: String = "Loading…",
    )

    val backups = mutableStateMapOf<String, BackupSub>()
    private val backupClients = HashMap<String, XtreamClient>()
    private var backupJob: Job? = null

    var useBackups by mutableStateOf(prefs.useBackups)
        private set

    fun turnBackups(on: Boolean) {
        useBackups = on
        prefs.useBackups = on
        if (on) loadBackups()
    }

    fun subscriptionName(subId: String?): String =
        subscriptions.firstOrNull { it.id == subId }?.name ?: activeSubscription?.name.orEmpty()

    fun clientFor(subId: String?): XtreamClient? = if (subId == null) client else backupClients[subId]

    fun agentFor(subId: String?): String =
        subscriptions.firstOrNull { it.id == (subId ?: activeSubscription?.id) }?.userAgent ?: UserAgents.current

    /** Quietly load every other subscription's channels, films and series. */
    fun loadBackups() {
        if (!useBackups) return
        val activeId = activeSubscription?.id
        val others = subscriptions.filter { it.id != activeId }
        backups.keys.filter { k -> others.none { it.id == k } }.forEach { backups.remove(it); backupClients.remove(it) }
        if (others.isEmpty()) return
        backupJob?.cancel()
        backupJob = viewModelScope.launch {
            for (sub in others) {
                val c = XtreamClient(sub.credentials, sub.userAgent)
                backupClients[sub.id] = c
                backups[sub.id] = (backups[sub.id] ?: BackupSub()).copy(status = "Loading…")
                try {
                    val chans = c.liveStreams().map { it.copy(subId = sub.id) }
                    backups[sub.id] = BackupSub(channels = chans, status = "Backup ready · ${chans.size} channels")
                    val films = runCatching { c.vodStreams().map { it.copy(subId = sub.id) } }.getOrDefault(emptyList())
                    val shows = runCatching { c.series().map { it.copy(subId = sub.id) } }.getOrDefault(emptyList())
                    backups[sub.id] = backups[sub.id]!!.copy(movies = films, series = shows)
                } catch (e: Exception) {
                    backups[sub.id] = BackupSub(status = "Couldn't load (${e.message ?: "error"})")
                }
            }
        }
    }

    private fun backupChannels(): List<Channel> =
        if (!useBackups) emptyList() else backups.values.flatMap { it.channels }

    /** Where Back from the player returns to. */
    private var returnScreen: Screen = Screen.Live

    var playlist by mutableStateOf<List<Channel>>(emptyList())
        private set
    var playingIndex by mutableIntStateOf(0)
        private set
    private var previousIndex: Int? = null


    /** Category ids the user has hidden for this subscription. */
    var hiddenCategories by mutableStateOf<Set<String>>(emptySet())
        private set

    /** Every channel not in a hidden category. Favourites always show. */
    val shownChannels: List<Channel> by derivedStateOf {
        val hidden = hiddenCategories
        if (hidden.isEmpty()) allChannels
        else allChannels.filter { it.categoryId !in hidden || it.streamId in favourites }
    }

    /** Show one entry per channel instead of every UHD/FHD/HD/SD version. */
    var mergeQualities by mutableStateOf(prefs.mergeQualities)
        private set

    fun setMergeQualitiesEnabled(on: Boolean) {
        mergeQualities = on
        prefs.mergeQualities = on
    }

    /** Collapse quality versions into one entry: the best version, under the tidy name. */
    private fun merge(list: List<Channel>): List<Channel> {
        if (!mergeQualities) return list
        val favs = favourites
        val groups = LinkedHashMap<String, MutableList<Channel>>()
        for (c in list) groups.getOrPut((c.subId ?: "") + "|" + com.matchday.tv.data.ChannelNames.key(c.name).ifEmpty { c.streamId }) { ArrayList() }.add(c)
        return groups.values.map { g ->
            val best = g.maxBy { com.matchday.tv.data.SportMatcher.channelScore(it, favs) }
            best.copy(name = com.matchday.tv.data.ChannelNames.clean(best.name), num = g.minOf { it.num })
        }
    }

    /** A channel's name as lists show it. */
    fun channelLabel(channel: Channel): String =
        if (mergeQualities) com.matchday.tv.data.ChannelNames.clean(channel.name) else channel.name

    val visibleChannels: List<Channel> by derivedStateOf {
        merge(
            when (selectedCategoryId) {
                CATEGORY_ALL -> shownChannels
                CATEGORY_FAVOURITES -> allChannels.filter { it.streamId in favourites }
                else -> allChannels.filter { it.categoryId == selectedCategoryId }
            }
        )
    }

    /** All provider categories, including hidden ones, for the manage page. */
    val allCategories: List<Category> get() = categories

    fun setCategoriesHidden(ids: Collection<String>, hidden: Boolean) {
        hiddenCategories = if (hidden) hiddenCategories + ids else hiddenCategories - ids.toSet()
        activeSubscription?.let { prefs.setHiddenCategories(it.id, hiddenCategories) }
        if (selectedCategoryId in hiddenCategories) selectedCategoryId = CATEGORY_ALL
    }

    fun categoryChannelCount(id: String): Int = allChannels.count { it.categoryId == id }

    /** Categories shown in the side list, with the two built-in ones first. */
    val categoryList: List<Category> by derivedStateOf {
        listOf(
            Category(CATEGORY_FAVOURITES, "Favourites"),
            Category(CATEGORY_ALL, "All channels"),
        ) + categories.filter { it.id !in hiddenCategories }
    }

    val currentChannel: Channel? get() = playlist.getOrNull(playingIndex)

    /** Every live channel from the provider. */
    val channels: List<Channel> get() = allChannels

    init {
        activeSubscription?.let { connect(it, attempts = 4) }
        updater.check(silent = true)
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

    var searchQuery by mutableStateOf("")

    /** Record live channels to storage so they can be paused and rewound. */
    var liveRewind by mutableStateOf(prefs.liveRewind)
        private set

    /** The single live player, shared by the previews and full screen. */
    val live = com.matchday.tv.player.LivePlayback(app)

    var previewEnabled by mutableStateOf(prefs.preview)
        private set

    fun turnPreview(on: Boolean) {
        previewEnabled = on
        prefs.preview = on
        if (!on && screen != Screen.Player) live.stop()
    }

    var stay50Hz by mutableStateOf(prefs.stay50Hz)
        private set

    fun setStay50HzEnabled(on: Boolean) {
        stay50Hz = on
        prefs.stay50Hz = on
    }

    /** Muted preview of a channel; keeps going if it's already playing this channel or one of its versions. */
    fun preview(channel: Channel) {
        if (!previewEnabled) return
        val current = live.currentStreamId
        if (current != null && sourcesFor(channel).any { it.uid == current }) return
        val best = sourcesFor(channel).first()
        val url = streamUrl(best) ?: return
        // Record from the start when live rewind is on, so pressing OK keeps rewind with no reconnect.
        live.start(url, best.uid, rewind = liveRewind, muted = true, userAgent = agentFor(best.subId))
    }

    /** Live TV only runs where it can be seen: full screen, or a preview on Live TV / the guide. */
    fun onScreenShown(s: Screen) {
        val keep = s == Screen.Player || (previewEnabled && (s == Screen.Live || s == Screen.Guide))
        if (!keep) live.stop()
    }

    override fun onCleared() {
        live.release()
        super.onCleared()
    }

    /** Fades and slides between screens. */
    var animations by mutableStateOf(prefs.animations)
        private set

    fun setAnimationsEnabled(on: Boolean) {
        animations = on
        prefs.animations = on
    }

    fun setLiveRewindEnabled(on: Boolean) {
        liveRewind = on
        prefs.liveRewind = on
    }

    /** Find channels, programmes (now and coming up), movies and series. Every word must match. */
    suspend fun search(query: String): SearchResults = withContext(Dispatchers.Default) {
        val words = query.lowercase().split(' ').filter { it.isNotBlank() }
        if (words.joinToString("").length < 2) return@withContext SearchResults()
        fun hit(text: String): Boolean { val t = text.lowercase(); return words.all { t.contains(it) } }
        val favs = favourites
        val chans = merge(shownChannels)
        val channelHits = chans.filter { hit(it.name) }
            .sortedByDescending { com.matchday.tv.data.SportMatcher.channelScore(it, favs) }.take(40)
        val bestByEpg = HashMap<String, Channel>()
        for (c in chans) {
            val id = c.epgId?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: continue
            val cur = bestByEpg[id]
            if (cur == null || com.matchday.tv.data.SportMatcher.channelScore(c, favs) > com.matchday.tv.data.SportMatcher.channelScore(cur, favs)) bestByEpg[id] = c
        }
        val now = System.currentTimeMillis()
        val progs = ArrayList<Pair<Channel, EpgEntry>>()
        for ((id, list) in guide) {
            val ch = bestByEpg[id] ?: continue
            for (p in list) if (p.end > now && hit(p.title)) progs += ch to p
        }
        val programmeHits = progs.sortedBy { it.second.start }
            .distinctBy { it.second.title.lowercase() + "|" + it.second.start / 60_000 }.take(40)
        // Other subscriptions: tagged results after the active one's.
        val extra = if (useBackups) backups.values.toList() else emptyList()
        val otherChannels = merge(extra.flatMap { it.channels }).filter { hit(it.name) }
            .sortedByDescending { com.matchday.tv.data.SportMatcher.channelScore(it, favs) }.take(20)
        SearchResults(
            channels = channelHits + otherChannels,
            programmes = programmeHits,
            movies = library.movies.filter { hit(it.name) }.take(40) + extra.flatMap { it.movies }.filter { hit(it.name) }.take(20),
            series = library.series.filter { hit(it.name) }.take(40) + extra.flatMap { it.series }.filter { hit(it.name) }.take(20),
        )
    }

    /** This channel plus other streams carrying the same thing (same guide id), best first. */
    fun sourcesFor(channel: Channel): List<Channel> {
        val original = (if (channel.subId == null) allChannels.firstOrNull { it.streamId == channel.streamId } else null) ?: channel
        val versions = if (mergeQualities) {
            val k = com.matchday.tv.data.ChannelNames.key(original.name)
            allChannels.filter { com.matchday.tv.data.ChannelNames.key(it.name) == k }
        } else emptyList()
        val id = original.epgId?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        val sameGuide = if (id == null) emptyList() else allChannels.filter { it.epgId?.trim()?.lowercase() == id && !it.name.contains("+1") }
        val own = (if (original.subId == null) versions + sameGuide else emptyList())
            .filter { it.uid != original.uid }
            .distinctBy { it.uid }
            .sortedByDescending { com.matchday.tv.data.SportMatcher.channelScore(it, favourites) }
        // Then the same channel on your other subscriptions (and the active one, for a channel found on a backup).
        val key = com.matchday.tv.data.ChannelNames.key(original.name)
        val pool = backupChannels() + (if (original.subId != null) allChannels else emptyList())
        val elsewhere = pool
            .filter { it.uid != original.uid && it.subId != original.subId || (original.subId != null && it.subId == null) }
            .filter { c ->
                c.uid != original.uid && !c.name.contains("+1") &&
                    (com.matchday.tv.data.ChannelNames.key(c.name) == key ||
                        (id != null && c.epgId?.trim()?.lowercase() == id))
            }
            .distinctBy { it.uid }
            .sortedWith(compareBy<Channel>({ if (it.subId == null) 0 else 1 }, { -com.matchday.tv.data.SportMatcher.channelScore(it, favourites) }))
        return (listOf(original) + own.take(5) + elsewhere.take(4)).take(10)
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
        backups.remove(sub.id)
        backupClients.remove(sub.id)
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
        if (sub.id != activeSubscription?.id) loadBackups()
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
        live.stop()
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
                hiddenCategories = prefs.hiddenCategories(sub.id)
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
                loadBackups()
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
        CATEGORY_ALL -> shownChannels.size
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
        val id = channel.uid
        if (!force && (epg.containsKey(id) || id in epgLoading)) return
        val c = clientFor(channel.subId) ?: return
        epgLoading += id
        viewModelScope.launch {
            try {
                epg[id] = c.shortEpg(channel.streamId)
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
        return if (!full.isNullOrEmpty()) full else epg[channel.uid].orEmpty()
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
        val list = visibleChannels.takeIf { l -> l.any { it.uid == channel.uid } }
            ?: shownChannels.takeIf { l -> l.any { it.uid == channel.uid } }
            ?: allChannels.takeIf { l -> l.any { it.uid == channel.uid } }
            ?: listOf(channel) // a channel from another subscription
        playlist = list
        playingIndex = list.indexOfFirst { it.uid == channel.uid }.coerceAtLeast(0)
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

    /** Play a channel from the in-player list. */
    fun playAt(index: Int) {
        if (index !in playlist.indices || index == playingIndex) return
        previousIndex = playingIndex
        playingIndex = index
        lastFocusedChannelId = currentChannel?.streamId
    }

    /** Jump back to the channel watched before the last switch. */
    fun lastChannel() {
        val prev = previousIndex ?: return
        previousIndex = playingIndex
        playingIndex = prev
        lastFocusedChannelId = currentChannel?.streamId
    }

    fun streamUrl(channel: Channel): String? {
        val fmt = subscriptions.firstOrNull { it.id == channel.subId }?.streamFormat ?: streamFormat
        return clientFor(channel.subId)?.streamUrl(channel, fmt)
    }

    fun closePlayer() {
        screen = returnScreen
    }
}
