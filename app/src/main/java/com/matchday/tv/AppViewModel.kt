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
    data object Recordings : Screen
    data object RecordingPlayer : Screen
    data object Downloads : Screen
    data object Catchup : Screen
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
const val CATEGORY_CATCHUP = "__catchup"

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

    // ---------- Downloads ----------
    init {
        com.matchday.tv.download.DownloadManager.init(app)
        com.matchday.tv.download.DownloadManager.wifiOnly = prefs.downloadWifiOnly
    }

    /** True when ShedTV opened without the internet and is showing downloads only. */
    var offline by mutableStateOf(false)
        private set
    val isOnline: Boolean get() = client != null

    var downloadWifiOnly by mutableStateOf(prefs.downloadWifiOnly)
        private set

    fun turnDownloadWifiOnly(on: Boolean) {
        downloadWifiOnly = on
        prefs.downloadWifiOnly = on
        com.matchday.tv.download.DownloadManager.wifiOnly = on
        com.matchday.tv.download.DownloadManager.kick()
    }

    var downloadFolderPref by mutableStateOf(prefs.downloadFolder)
        private set

    /** Where new downloads go: a folder the user picked, a chosen storage volume, or this device. */
    fun downloadFolder(): String? {
        val vols = com.matchday.tv.download.DownloadManager.volumes()
        val p = downloadFolderPref
        if (p != null && p.startsWith("content://")) {
            val granted = getApplication<Application>().contentResolver.persistedUriPermissions.any { it.uri.toString() == p && it.isWritePermission }
            if (granted) return p
        }
        return vols.firstOrNull { it.dir.path == p }?.dir?.path ?: vols.firstOrNull()?.dir?.path
    }

    fun setDownloadFolder(value: String) {
        downloadFolderPref = value
        prefs.downloadFolder = value
    }

    fun pickedDownloadFolder(uri: android.net.Uri) {
        runCatching {
            getApplication<Application>().contentResolver.takePersistableUriPermission(uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            setDownloadFolder(uri.toString())
        }.onFailure { flash = "Couldn't use that folder." }
    }

    fun downloadLocationLabel(): String {
        val f = downloadFolder() ?: return "No storage available"
        if (f.startsWith("content://")) return "Folder: " + com.matchday.tv.download.DownloadManager.folderName(f)
        val v = com.matchday.tv.download.DownloadManager.volumes().firstOrNull { it.dir.path == f }
        return (v?.label ?: "This device") + (v?.let { " · %.1f GB free".format(it.freeBytes / 1073741824.0) } ?: "")
    }

    private fun queueDownload(p: com.matchday.tv.data.Playable, subId: String?, season: Int, episode: Int, fileName: String): Boolean {
        val folder = downloadFolder() ?: run { flash = "No storage available for downloads."; return false }
        if (com.matchday.tv.download.DownloadManager.find(p.id) != null) return false
        com.matchday.tv.download.DownloadManager.add(
            com.matchday.tv.download.DownloadItem(
                id = System.nanoTime().toString(36), kind = p.kind, historyId = p.id, parentId = p.parentId,
                title = p.title, subtitle = p.subtitle, image = p.image, season = season, episode = episode,
                url = p.url, userAgent = p.userAgent ?: UserAgents.current, subId = subId ?: activeSubscription?.id,
                fileName = fileName + "." + p.url.substringAfterLast('.', "mkv").take(5), location = "",
                totalBytes = 0, doneBytes = 0, status = "queued", message = "", position = 0, duration = 0, added = 0,
            ),
            folder,
        )
        return true
    }

    fun downloadMovie(item: com.matchday.tv.data.VodItem) {
        val p = library.moviePlayable(item, fromStart = true) ?: return
        val year = library.movieInfo?.takeIf { library.openMovie?.id == item.id }?.year?.take(4)?.takeIf { it.length == 4 }
        if (queueDownload(p, item.subId, 0, 0, item.name + (year?.let { " ($it)" } ?: "")))
            flash = "Downloading ${item.name}" + if (downloadWifiOnly) " (on Wi-Fi)" else ""
    }

    fun downloadEpisodes(show: com.matchday.tv.data.SeriesItem, episodes: List<com.matchday.tv.data.Episode>, indices: List<Int>) {
        var n = 0
        indices.forEach { i ->
            val ep = episodes.getOrNull(i) ?: return@forEach
            val p = library.episodePlayable(show, episodes, i, fromStart = true)?.copy(upNext = emptyList()) ?: return@forEach
            val name = "%s - S%02dE%02d".format(show.name, ep.season, ep.num) + (ep.title.takeIf { it.isNotBlank() && !it.contains(show.name, true) }?.let { " - $it" } ?: "")
            if (queueDownload(p, show.subId, ep.season, ep.num, name)) n++
        }
        flash = when {
            n == 0 -> "Already in Downloads."
            n == 1 -> "Added 1 episode to Downloads"
            else -> "Added $n episodes to Downloads"
        }
    }

    /** Play a finished download, with the following downloaded episodes as up next. */
    fun playDownload(d: com.matchday.tv.download.DownloadItem) {
        fun playable(x: com.matchday.tv.download.DownloadItem, start: Long) = com.matchday.tv.data.Playable(
            kind = x.kind, id = x.historyId, title = x.title, subtitle = x.subtitle, url = x.playUri, image = x.image,
            parentId = x.parentId, startAt = start, downloadId = x.id,
        )
        val online = if (isOnline) library.resumePoint(d.kind, d.historyId)?.position else null
        val start = online ?: d.position.takeIf { d.duration > 0 && d.position < d.duration - 120_000 } ?: 0
        val next = if (d.kind == "episode") com.matchday.tv.download.DownloadManager.items
            .filter { it.status == "done" && it.parentId == d.parentId && (it.season to it.episode).let { (s, e) -> s > d.season || (s == d.season && e > d.episode) } }
            .sortedWith(compareBy({ it.season }, { it.episode })).map { playable(it, 0) } else emptyList()
        playVod(playable(d, start).copy(upNext = next))
    }

    fun goOnline() {
        offline = false
        activeSubscription?.let { connect(it, attempts = 2) } ?: run { screen = Screen.Login }
    }

    private fun holdDownloadsFor(s: Screen) {
        val watching = s == Screen.Player || s == Screen.Catchup || s == Screen.VodPlayer && library.playing?.downloadId == null ||
            (previewEnabled && (s == Screen.Live || s == Screen.Guide))
        val sub = activeSubscription?.id
        val hold = if (watching && sub != null && limitFor(null) <= 1) sub else null
        val dm = com.matchday.tv.download.DownloadManager
        if (hold != null && dm.holdSub != hold && dm.items.any { it.status == "downloading" && it.subId == hold }) {
            flash = "Downloads paused while you watch (1 connection). They carry on afterwards."
        }
        dm.hold(hold)
    }

    // ---------- Recording ----------
    init { com.matchday.tv.record.RecorderManager.init(app) }

    var recordingEnabled by mutableStateOf(prefs.recording)
        private set
    var recordingDir by mutableStateOf(prefs.recordingDir)
        private set
    var playingRecordingId by mutableStateOf<String?>(null)
        private set
    var playingRecordingTail = false
        private set

    /** Short message shown at the bottom of any screen for a few seconds. */
    var flash by mutableStateOf<String?>(null)

    fun turnRecording(on: Boolean) {
        recordingEnabled = on
        prefs.recording = on
    }

    fun chooseRecordingVolume(v: com.matchday.tv.record.RecorderManager.Volume) {
        recordingDir = v.dir.path
        prefs.recordingDir = v.dir.path
    }

    fun recordingVolume(): com.matchday.tv.record.RecorderManager.Volume? {
        val vols = com.matchday.tv.record.RecorderManager.volumes()
        return vols.firstOrNull { it.dir.path == recordingDir } ?: vols.firstOrNull()
    }

    fun recordingStorageLine(): String {
        val v = recordingVolume() ?: return "No storage available"
        val hours = v.freeBytes / com.matchday.tv.record.RecorderManager.BYTES_PER_HOUR
        return "${v.label}: %.1f GB free (about $hours h of HD)".format(v.freeBytes / 1073741824.0)
    }

    /** Max connections for a subscription (from its login); 1 when unknown, to be safe. */
    private val connectionLimits = HashMap<String, Int>()
    private fun limitFor(subId: String?): Int = connectionLimits[subId ?: activeSubscription?.id ?: ""] ?: 1

    private fun subOfUid(uid: String): String? = uid.substringBefore(':').ifEmpty { null }

    fun recordingFor(channel: Channel): com.matchday.tv.record.RecordingInfo? =
        com.matchday.tv.record.RecorderManager.recordings.firstOrNull { it.active && it.channel == channelLabel(channel) }

    /**
     * Start recording [channel] until [endAt]. Thinks about connections first: on a one-connection
     * subscription, recording what you're watching moves you onto the recording; recording a
     * different channel uses another subscription first if there is one.
     */
    fun startRecording(channel: Channel, title: String, endAt: Long) {
        val volume = recordingVolume() ?: run { flash = "No storage available for recordings."; return }
        if (volume.freeBytes < 500L * 1024 * 1024) { flash = "Not enough free space to record (${volume.label})."; return }
        var sources = sourcesFor(channel)
        val watching = live.currentStreamId
        var watchFromRecording = false
        if (watching != null && subOfUid(watching) == channel.subId && limitFor(channel.subId) <= 1) {
            if (sources.any { it.uid == watching }) {
                watchFromRecording = true
            } else {
                val elsewhere = sources.filter { it.subId != channel.subId }
                if (elsewhere.isNotEmpty()) sources = elsewhere + sources.filter { it.subId == channel.subId }
                else flash = "${subscriptionName(channel.subId)} allows 1 connection: changing channel may interrupt this recording."
            }
        }
        val recSources = sources.mapNotNull { ch ->
            val url = clientFor(ch.subId)?.streamUrl(ch, "ts") ?: return@mapNotNull null
            val q = com.matchday.tv.data.ChannelNames.quality(ch.name)
            com.matchday.tv.record.RecSource(url, agentFor(ch.subId), subscriptionName(ch.subId) + if (q.isNotEmpty()) " $q" else "", ch.subId)
        }
        if (watchFromRecording) live.stop()
        val id = com.matchday.tv.record.RecorderManager.start(title, channelLabel(channel), recSources, endAt, volume)
        if (watchFromRecording) {
            playRecording(id, tail = true)
            flash = "Recording. You're now watching from the recording, so it uses just one connection."
        } else if (flash == null) {
            flash = "Recording ${title} until ${java.text.SimpleDateFormat("HH:mm", java.util.Locale.UK).format(java.util.Date(endAt))}"
        }
    }

    fun playRecording(id: String, tail: Boolean) {
        playingRecordingId = id
        playingRecordingTail = tail
        live.stop()
        screen = Screen.RecordingPlayer
    }

    /** Warning when live TV would share a one-connection subscription with a recording. */
    fun recordingConflict(channel: Channel): String? {
        if (com.matchday.tv.record.RecorderManager.activeCount() == 0 || limitFor(channel.subId) > 1) return null
        val rec = com.matchday.tv.record.RecorderManager.recordings.firstOrNull { it.active } ?: return null
        if (rec.channel == channelLabel(channel)) return null
        return "Recording ${rec.title}: ${subscriptionName(channel.subId)} allows 1 connection, so the recording may switch to a backup or pause."
    }

    // ---------- Subtitles ----------
    private var subtitlesOn = prefs.subtitles
    private var subtitleLang = prefs.subtitleLanguage

    fun setSubtitlePref(on: Boolean, language: String?) {
        subtitlesOn = on
        prefs.subtitles = on
        if (on && !language.isNullOrBlank() && language != "und") {
            subtitleLang = language
            prefs.subtitleLanguage = language
        }
    }

    /** Apply the remembered subtitle choice to a player before something new plays. */
    fun applySubtitlePrefs(player: androidx.media3.common.Player) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(androidx.media3.common.C.TRACK_TYPE_TEXT)
            .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, !subtitlesOn)
            .setPreferredTextLanguage(subtitleLang ?: "en")
            .setSelectUndeterminedTextLanguage(true)
            .build()
    }

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
                    runCatching { c.login() }.getOrNull()?.let { noteAccount(sub.id, it) }
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
                CATEGORY_CATCHUP -> catchupChannels
                else -> allChannels.filter { it.categoryId == selectedCategoryId }
            }
        )
    }

    /** All provider categories, including hidden ones, for the manage page. */
    val allCategories: List<Category> get() = orderedCategories

    fun setCategoriesHidden(ids: Collection<String>, hidden: Boolean) {
        hiddenCategories = if (hidden) hiddenCategories + ids else hiddenCategories - ids.toSet()
        activeSubscription?.let { prefs.setHiddenCategories(it.id, hiddenCategories) }
        if (selectedCategoryId in hiddenCategories) selectedCategoryId = CATEGORY_ALL
    }

    fun categoryChannelCount(id: String): Int = allChannels.count { it.categoryId == id }

    /** Categories shown in the side list, with the two built-in ones first. */
    /** Category ids in the user's order (per subscription); empty means the provider's order. */
    var categoryOrder by mutableStateOf<List<String>>(emptyList())
        private set

    private val orderedCategories: List<Category> by derivedStateOf {
        val order = categoryOrder
        if (order.isEmpty()) categories
        else {
            val pos = order.withIndex().associate { (i, id) -> id to i }
            // Known ones in your order; new categories from the provider go at the end in its order.
            categories.withIndex().sortedBy { (i, c) -> pos[c.id] ?: (order.size + i) }.map { it.value }
        }
    }

    fun moveCategory(id: String, delta: Int) {
        val ids = orderedCategories.map { it.id }.toMutableList()
        val from = ids.indexOf(id)
        if (from < 0) return
        val to = (from + delta).coerceIn(0, ids.lastIndex)
        if (to == from) return
        ids.removeAt(from)
        ids.add(to, id)
        categoryOrder = ids
        activeSubscription?.let { prefs.setCategoryOrder(it.id, ids) }
    }

    fun resetCategoryOrder() {
        categoryOrder = emptyList()
        activeSubscription?.let { prefs.setCategoryOrder(it.id, emptyList()) }
    }

    /** Every channel your provider keeps catch-up for (from categories you haven't hidden). */
    private val catchupChannels: List<Channel> by derivedStateOf { shownChannels.filter { it.hasArchive } }

    val categoryList: List<Category> by derivedStateOf {
        listOf(
            Category(CATEGORY_FAVOURITES, "Favourites"),
            Category(CATEGORY_ALL, "All channels"),
        ) + (if (catchupChannels.isNotEmpty()) listOf(Category(CATEGORY_CATCHUP, "⟲ Catch-up")) else emptyList()) + orderedCategories.filter { it.id !in hiddenCategories }
    }

    val currentChannel: Channel? get() = playlist.getOrNull(playingIndex)

    /** Every live channel from the provider. */
    val channels: List<Channel> get() = allChannels

    init {
        val cm = app.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        if (cm.activeNetwork == null && com.matchday.tv.download.DownloadManager.items.any { it.status == "done" }) {
            // No internet at all (on a plane): go straight to downloads.
            offline = true
            screen = Screen.Downloads
        } else {
            activeSubscription?.let { connect(it, attempts = 4) }
        }
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
        holdDownloadsFor(s)
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
                        val account = c.login()
                        noteAccount(sub.id, account)
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
                categoryOrder = prefs.categoryOrder(sub.id)
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
                val noNetwork = e is java.net.UnknownHostException || e is java.net.ConnectException || e is java.net.SocketTimeoutException
                if (noNetwork && !hadSession && com.matchday.tv.download.DownloadManager.items.any { it.status == "done" }) {
                    offline = true
                    screen = Screen.Downloads
                    return@launch
                }
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
        CATEGORY_CATCHUP -> catchupChannels.size
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
        val base = if (!full.isNullOrEmpty()) full else epg[channel.uid].orEmpty()
        val past = archiveEpg[channel.uid]
        if (past.isNullOrEmpty()) return base
        // Older programmes (from the catch-up listing) before the guide's own data.
        val firstStart = base.firstOrNull()?.start ?: Long.MAX_VALUE
        return past.filter { it.end <= firstStart } + base
    }

    // ---------- Catch-up ----------
    private val archiveEpg = androidx.compose.runtime.mutableStateMapOf<String, List<EpgEntry>>()
    private val archiveLoading = HashSet<String>()
    private val serverClocks = HashMap<String, Pair<Long?, String?>>()

    private fun noteAccount(subId: String, account: com.matchday.tv.data.AccountInfo) {
        account.maxConnections?.toIntOrNull()?.let { connectionLimits[subId] = it }
        serverClocks[subId] = account.serverOffsetMs to account.timezone
    }

    /** Load past programmes for a catch-up channel (once). */
    fun loadArchiveEpg(channel: Channel) {
        if (!channel.hasArchive) return
        val id = channel.uid
        if (archiveEpg.containsKey(id) || id in archiveLoading) return
        val c = clientFor(channel.subId) ?: return
        archiveLoading += id
        viewModelScope.launch {
            try {
                archiveEpg[id] = c.archiveEpg(channel.streamId)
            } catch (_: Exception) {
                archiveEpg[id] = emptyList()
            } finally {
                archiveLoading -= id
            }
        }
    }

    /** How far back catch-up goes on this channel (0 if it has none). */
    fun archiveMs(channel: Channel): Long = if (!channel.hasArchive) 0 else (channel.archiveDays.takeIf { it > 0 } ?: 1).coerceAtMost(14) * 24 * 60 * 60 * 1000L

    /** Can this programme be watched from its start? */
    fun canCatchUp(channel: Channel, entry: EpgEntry?, now: Long = System.currentTimeMillis()): Boolean =
        entry != null && channel.hasArchive && entry.start < now - 60_000 && entry.start > now - archiveMs(channel)

    /** The provider's time zone for building catch-up addresses. */
    private fun serverZone(subId: String?): java.util.TimeZone {
        val (offset, name) = serverClocks[subId ?: activeSubscription?.id ?: ""] ?: (null to null)
        val named = name?.let { java.util.TimeZone.getTimeZone(it) }?.takeIf { it.id != "GMT" || name == "GMT" }
        return when {
            named != null && (offset == null || named.getOffset(System.currentTimeMillis()).toLong() == offset) -> named
            offset != null -> java.util.SimpleTimeZone(offset.toInt(), "server")
            named != null -> named
            else -> java.util.TimeZone.getDefault()
        }
    }

    /** Catch-up address for [channel] from [startWall] (UTC ms) for [durationMin] minutes, in one of two URL styles. */
    fun catchupUrl(channel: Channel, startWall: Long, durationMin: Int, style: Int): String? {
        val c = clientFor(channel.subId) ?: return null
        val f = java.text.SimpleDateFormat("yyyy-MM-dd:HH-mm", java.util.Locale.US).apply { timeZone = serverZone(channel.subId) }
        return c.catchupUrl(channel.streamId, f.format(java.util.Date(startWall)), durationMin.coerceIn(5, 600), style)
    }

    /** Versions of [channel] (and backup subscriptions) that keep catch-up, best first. */
    fun catchupSources(channel: Channel): List<Channel> = sourcesFor(channel).filter { it.hasArchive }

    data class CatchupRequest(val channel: Channel, val title: String, val progStart: Long, val progEnd: Long, val from: Long, val returnTo: Screen)
    var catchup by mutableStateOf<CatchupRequest?>(null)
        private set

    /** Watch [entry] on [channel] from the start (2 minutes early, as guide times are often a little off). */
    fun playCatchup(channel: Channel, entry: EpgEntry, from: Screen) {
        live.stop()
        catchup = CatchupRequest(channel, entry.title, entry.start, entry.end, entry.start - 2 * 60_000, from)
        screen = Screen.Catchup
    }

    /** "Watch from the start" while watching live: Back afterwards returns where live TV would. */
    fun catchupFromPlayer(channel: Channel, entry: EpgEntry) = playCatchup(channel, entry, returnScreen)

    /** From catch-up straight into the live channel. */
    fun joinLive(channel: Channel, from: Screen) {
        catchup = null
        play(channel, from)
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
        // Play the downloaded copy when there is one.
        val d = if (p.downloadId == null) com.matchday.tv.download.DownloadManager.find(p.id)?.takeIf { it.status == "done" } else null
        library.playing = if (d != null) p.copy(url = d.playUri, downloadId = d.id) else p
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
