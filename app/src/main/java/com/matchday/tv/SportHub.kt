package com.matchday.tv

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.matchday.tv.data.Channel
import com.matchday.tv.data.EpgEntry
import com.matchday.tv.data.Fixture
import com.matchday.tv.data.FollowedTeams
import com.matchday.tv.data.SportMatcher
import com.matchday.tv.data.SportsDb
import com.matchday.tv.data.Team
import com.matchday.tv.data.TeamsCatalog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Followed teams, crests, and the fixtures found in the TV guide. */
class SportHub(app: Application, private val scope: CoroutineScope) {
    private val store = FollowedTeams(app)

    /** Crests for the built-in teams, looked up at build time and shipped with the app. */
    private val bundled: Map<String, String> = try {
        val text = app.assets.open("crests.json").bufferedReader().use { it.readText() }
        (kotlinx.serialization.json.Json.parseToJsonElement(text) as kotlinx.serialization.json.JsonObject)
            .mapNotNull { (k, v) -> (v as? kotlinx.serialization.json.JsonPrimitive)?.content?.let { k to it } }.toMap()
    } catch (e: Exception) {
        emptyMap()
    }

    var followed by mutableStateOf(store.load())
        private set
    var badges by mutableStateOf(bundled + store.badges())
        private set

    /** Progress of crest lookups, shown in the team picker; null when idle. */
    var badgeStatus by mutableStateOf<String?>(null)
        private set
    var live by mutableStateOf<List<Fixture>>(emptyList())
        private set
    var upcoming by mutableStateOf<List<Fixture>>(emptyList())
        private set
    /** Any matches starting in the next 12 hours, for the panel beside the featured match. */
    var comingUp by mutableStateOf<List<Fixture>>(emptyList())
        private set
    var searchResults by mutableStateOf<List<Team>>(emptyList())
        private set
    var searching by mutableStateOf(false)
        private set
    var searchMessage by mutableStateOf<String?>(null)
        private set

    /** A followed team's match that is about to kick off; shown as a banner. */
    var alert by mutableStateOf<Fixture?>(null)
        private set
    private val alerted = HashSet<String>()

    fun dismissAlert() {
        alert = null
    }

    private val badgeQueue = LinkedHashMap<String, Team>()
    private var badgeJob: Job? = null

    val followedKeys: Set<String> get() = followed.mapTo(HashSet()) { it.key }

    fun isFollowed(team: Team) = followed.any { it.key == team.key }

    fun toggle(team: Team) {
        followed = if (isFollowed(team)) followed.filterNot { it.key == team.key }
        else followed + team.copy(badge = badgeOf(team))
        store.save(followed)
    }

    fun badgeOf(team: Team?): String? = team?.let { it.badge ?: badges[it.key] }

    /** Look up crests for these teams in the background, one every couple of seconds. */
    fun requestBadges(teams: List<Team>) {
        teams.filter { badgeOf(it) == null }.forEach { badgeQueue.putIfAbsent(it.key, it) }
        if (badgeJob?.isActive == true || badgeQueue.isEmpty()) return
        badgeJob = scope.launch {
            var backoff = 0L
            while (badgeQueue.isNotEmpty()) {
                val key = badgeQueue.keys.first()
                val team = badgeQueue[key] ?: break
                badgeStatus = "Finding crests… ${badgeQueue.size} to go"
                val result = SportsDb.badgeFor(team)
                when {
                    result.url != null -> {
                        badgeQueue.remove(key)
                        backoff = 0
                        badges = badges + (key to result.url)
                        store.saveBadges(badges)
                        if (isFollowed(team)) {
                            followed = followed.map { if (it.key == key) it.copy(badge = result.url) else it }
                            store.save(followed)
                        }
                    }
                    result.failed -> {
                        // Rate-limited or offline: wait and try the same team again.
                        backoff = (if (backoff == 0L) 20_000L else backoff * 2).coerceAtMost(180_000L)
                        badgeStatus = "Crest service busy, retrying in ${backoff / 1000}s…"
                        kotlinx.coroutines.delay(backoff)
                    }
                    else -> badgeQueue.remove(key) // genuinely not found; keep its colour badge
                }
            }
            badgeStatus = null
        }
    }

    fun search(query: String) {
        if (query.isBlank()) return
        scope.launch {
            searching = true
            searchMessage = null
            searchResults = try {
                SportsDb.searchTeams(query) ?: throw java.io.IOException("the team service is busy, try again in a minute")
            } catch (e: Exception) {
                searchMessage = "Search isn't working right now (${e.message})."
                emptyList()
            }
            if (searchResults.isEmpty() && searchMessage == null) searchMessage = "No football or rugby league teams found for \"$query\"."
            searching = false
        }
    }

    /** Re-scan the guide for live and upcoming fixtures. */
    suspend fun refresh(channels: List<Channel>, guide: Map<String, List<EpgEntry>>, favourites: Set<String>) {
        val teams = SportMatcher.index((TeamsCatalog.all + followed).distinctBy { it.key })
        val keys = followedKeys
        val now = System.currentTimeMillis()
        val (liveList, upcomingList) = withContext(Dispatchers.Default) {
            val found = ArrayList<Fixture>()
            val byEpg = channels.filter { !it.epgId.isNullOrBlank() }.groupBy { it.epgId!!.trim().lowercase() }
            for ((epgId, chans) in byEpg) {
                val programmes = guide[epgId] ?: continue
                val ranked = chans.sortedByDescending { SportMatcher.channelScore(it, favourites) }
                val best = ranked.first()
                for (p in programmes) {
                    if (p.end < now || p.start > now + WEEK) continue
                    val f = SportMatcher.parse(p, best, teams) ?: continue
                    found += f.copy(alternatives = ranked.drop(1))
                }
            }
            // The same match is often on several channels: keep the best, remember the rest as backups.
            val merged = found
                .groupBy { SportMatcher.normalise(it.title) + "|" + it.entry.start / 60_000 }
                .map { (_, group) ->
                    val sorted = group.sortedByDescending { SportMatcher.channelScore(it.channel, favourites) + if (it.live) 5 else 0 }
                    val top = sorted.first()
                    top.copy(alternatives = (sorted.drop(1).map { it.channel } + top.alternatives).distinctBy { it.streamId })
                }
            fun rank(f: Fixture) = (if (f.involves(keys)) 0 else 2) + (if (f.homeTeam != null && f.awayTeam != null) 0 else 1)
            val liveNow = merged
                .filter { it.entry.isOn(now) && (it.live || (it.homeTeam != null && it.awayTeam != null)) }
                .sortedWith(compareBy<Fixture>({ rank(it) }, { it.entry.start }))
                .take(30)
            val next = merged
                .filter { it.entry.start > now && it.involves(keys) && (it.live || (it.homeTeam != null && it.awayTeam != null)) }
                .sortedBy { it.entry.start }
                .distinctBy { SportMatcher.normalise(it.title) + (it.entry.start / 3_600_000) }
                .take(30)
            val soon = merged
                .filter { it.entry.start > now && it.entry.start < now + 12 * 60 * 60_000L && (it.live || (it.homeTeam != null && it.awayTeam != null)) }
                .sortedWith(compareBy<Fixture>({ it.entry.start }, { rank(it) }))
                .distinctBy { SportMatcher.normalise(it.title) }
                .take(5)
            Triple(liveNow, next, soon)
        }.let { (a, b, c) ->
            comingUp = c
            a to b
        }
        live = liveList
        upcoming = upcomingList
        // Kick-off alert: one of your teams starts within 5 minutes (or started in the last 10).
        val due = (liveList + upcomingList).firstOrNull {
            it.involves(keys) && it.entry.start - now < 5 * 60_000 && now - it.entry.start < 10 * 60_000 &&
                alerted.add(SportMatcher.normalise(it.title) + it.entry.start / 3_600_000)
        }
        if (due != null) alert = due
    }

    companion object {
        private const val WEEK = 7 * 24 * 60 * 60 * 1000L
    }
}
