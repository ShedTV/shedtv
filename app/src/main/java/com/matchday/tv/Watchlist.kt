package com.matchday.tv

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.matchday.tv.data.Channel
import com.matchday.tv.data.EpgEntry
import com.matchday.tv.data.FollowedTeams
import com.matchday.tv.data.SportMatcher
import com.matchday.tv.data.Team
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/** Something to look out for in the guide: a few words, or a team (name plus the other names guides use). */
data class WatchItem(
    val id: String,
    val label: String,
    val phrases: List<String>,
    /** Set for teams: shown as coloured initials. */
    val short: String? = null,
    val colour: Long? = null,
    val textColour: Long? = null,
    val teamKey: String? = null,
)

/** A programme in your guide that matches a watchlist item. */
data class WatchMatch(val item: WatchItem, val entry: EpgEntry, val channel: Channel, val alternatives: List<Channel>)

/** The watchlist, and what in the guide matches it. Works for anything: teams, shows, events. */
class Watchlist(app: Application, @Suppress("unused") private val scope: CoroutineScope) {
    private val sp = app.getSharedPreferences("shedtv_watchlist", Context.MODE_PRIVATE)

    private var migrated = false
    var items by mutableStateOf(load(app))
        private set

    init { if (migrated) save() }
    var onNow by mutableStateOf<List<WatchMatch>>(emptyList())
        private set
    var comingUp by mutableStateOf<List<WatchMatch>>(emptyList())
        private set
    /** Something on the watchlist starting now; shown as a banner. */
    var alert by mutableStateOf<WatchMatch?>(null)
        private set
    private val alerted = HashSet<String>()

    fun dismissAlert() { alert = null }

    fun hasTeam(team: Team) = items.any { it.teamKey == team.key }

    fun addWords(text: String): Boolean {
        val t = text.trim()
        if (t.length < 2 || items.any { it.label.equals(t, true) }) return false
        items = items + WatchItem(id = "w" + System.nanoTime().toString(36), label = t, phrases = listOf(t))
        save()
        return true
    }

    fun toggleTeam(team: Team) {
        items = if (hasTeam(team)) items.filterNot { it.teamKey == team.key }
        else items + WatchItem("t-" + team.key, team.name, team.aliases.ifEmpty { listOf(team.name) }, team.short, team.colour, team.textColour, team.key)
        save()
    }

    fun remove(id: String) {
        items = items.filterNot { it.id == id }
        save()
    }

    fun move(id: String, by: Int) {
        val i = items.indexOfFirst { it.id == id }
        if (i < 0) return
        val j = (i + by).coerceIn(0, items.lastIndex)
        if (i == j) return
        items = items.toMutableList().apply { add(j, removeAt(i)) }
        save()
    }

    /** Re-scan the guide for anything on the watchlist (now, and the next 24 hours). */
    suspend fun refresh(channels: List<Channel>, guide: Map<String, List<EpgEntry>>, favourites: Set<String>) {
        val list = items
        if (list.isEmpty()) { onNow = emptyList(); comingUp = emptyList(); return }
        val now = System.currentTimeMillis()
        val (a, b) = withContext(Dispatchers.Default) {
            val needles = list.map { it to it.phrases.map { p -> " " + SportMatcher.normalise(p) + " " }.filter { it.isNotBlank() } }
            val found = ArrayList<WatchMatch>()
            val byEpg = channels.filter { !it.epgId.isNullOrBlank() }.groupBy { it.epgId!!.trim().lowercase() }
            for ((epgId, chans) in byEpg) {
                val programmes = guide[epgId] ?: continue
                val ranked = chans.sortedByDescending { SportMatcher.channelScore(it, favourites) }
                for (p in programmes) {
                    if (p.end < now || p.start > now + DAY) continue
                    val title = " " + SportMatcher.normalise(p.title) + " "
                    val hit = needles.firstOrNull { (_, ns) -> ns.any { title.contains(it) } }?.first ?: continue
                    found += WatchMatch(hit, p, ranked.first(), ranked.drop(1))
                }
            }
            // The same programme is often on several channels: keep the best, the rest are backups.
            val merged = found.groupBy { SportMatcher.normalise(it.entry.title) + "|" + it.entry.start / 60_000 }.map { (_, g) ->
                val sorted = g.sortedByDescending { SportMatcher.channelScore(it.channel, favourites) }
                sorted.first().copy(alternatives = (sorted.drop(1).map { it.channel } + sorted.first().alternatives).distinctBy { it.uid })
            }
            val order = list.withIndex().associate { it.value.id to it.index }
            merged.filter { it.entry.isOn(now) }.sortedWith(compareBy({ order[it.item.id] ?: 99 }, { it.entry.start })).take(30) to
                merged.filter { it.entry.start > now }.sortedBy { it.entry.start }.take(40)
        }
        onNow = a
        comingUp = b
        // Starts within 5 minutes (or started in the last 5): one banner each.
        val due = (b + a).firstOrNull {
            it.entry.start - now < 5 * 60_000 && now - it.entry.start < 5 * 60_000 &&
                alerted.add(SportMatcher.normalise(it.entry.title) + it.entry.start / 60_000)
        }
        if (due != null) alert = due
    }

    private fun save() {
        val arr = buildJsonArray {
            items.forEach { w ->
                add(buildJsonObject {
                    put("id", w.id); put("label", w.label)
                    put("phrases", buildJsonArray { w.phrases.forEach { add(JsonPrimitive(it)) } })
                    w.short?.let { put("short", it) }; w.colour?.let { put("colour", it.toString()) }
                    w.textColour?.let { put("textColour", it.toString()) }; w.teamKey?.let { put("teamKey", it) }
                })
            }
        }
        sp.edit().putString("items", arr.toString()).apply()
    }

    private fun load(app: Application): List<WatchItem> {
        val raw = sp.getString("items", null)
        if (raw == null) {
            // First run after the Sport Hub: bring followed teams across.
            val teams = runCatching { FollowedTeams(app).load() }.getOrDefault(emptyList())
            return teams.map { t -> WatchItem("t-" + t.key, t.name, t.aliases.ifEmpty { listOf(t.name) }, t.short, t.colour, t.textColour, t.key) }
                .also { migrated = it.isNotEmpty() }
        }
        return runCatching {
            (Json.parseToJsonElement(raw) as JsonArray).mapNotNull { el ->
                val o = el as? JsonObject ?: return@mapNotNull null
                fun s(k: String) = (o[k] as? JsonPrimitive)?.contentOrNull
                WatchItem(
                    id = s("id") ?: return@mapNotNull null, label = s("label").orEmpty(),
                    phrases = (o["phrases"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty(),
                    short = s("short"), colour = s("colour")?.toLongOrNull(), textColour = s("textColour")?.toLongOrNull(), teamKey = s("teamKey"),
                )
            }
        }.getOrDefault(emptyList())
    }

    companion object {
        private const val DAY = 24 * 60 * 60 * 1000L
    }
}
