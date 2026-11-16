package com.matchday.tv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.Request
import java.net.URLEncoder

/**
 * TheSportsDB free API (public key "123"): team search and crests.
 * The free tier allows ~30 requests a minute, so callers space requests out.
 */
object SportsDb {
    private const val BASE = "https://www.thesportsdb.com/api/v1/json/123"
    private var lastCall = 0L

    suspend fun searchTeams(query: String): List<Team> = withContext(Dispatchers.IO) {
        throttle()
        val url = "$BASE/searchteams.php?t=" + URLEncoder.encode(query.trim(), "UTF-8")
        val body = XtreamClient.http.newCall(Request.Builder().url(url).header("User-Agent", USER_AGENT).build())
            .execute().use { if (it.isSuccessful) it.body?.string() else null } ?: return@withContext emptyList()
        val root = runCatching { Json.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return@withContext emptyList()
        val teams = root["teams"] as? JsonArray ?: return@withContext emptyList()
        teams.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val sportRaw = o.s("strSport").orEmpty()
            val leagueRaw = o.s("strLeague").orEmpty()
            val sport = when {
                sportRaw.equals("Soccer", true) -> FOOTBALL
                sportRaw.contains("Rugby League", true) -> RUGBY_LEAGUE
                sportRaw.contains("Rugby", true) && leagueRaw.contains("League", true) && !leagueRaw.contains("Union", true) -> RUGBY_LEAGUE
                else -> return@mapNotNull null
            }
            val name = o.s("strTeam") ?: return@mapNotNull null
            val alt = o.s("strTeamAlternate").orEmpty().split(',').map { it.trim() }.filter { it.length > 2 }
            val short = o.s("strTeamShort")?.takeIf { it.length in 2..4 } ?: name.filter { it.isLetter() }.take(3)
            Team(
                key = "tsdb-" + (o.s("idTeam") ?: name),
                name = name,
                short = short.uppercase(),
                sport = sport,
                league = o.s("strLeague").orEmpty(),
                aliases = (listOf(name) + alt).distinct(),
                colour = parseColour(o.s("strColour1")) ?: 0xFF16202A,
                textColour = parseColour(o.s("strColour2")) ?: 0xFFFFFFFF,
                badge = smallBadge(o.s("strBadge") ?: o.s("strTeamBadge")),
                lookup = name,
            )
        }
    }

    /** Find a crest for one of our built-in teams. */
    suspend fun badgeFor(team: Team): String? {
        val results = runCatching { searchTeams(team.lookup) }.getOrDefault(emptyList())
        val sameSport = results.filter { it.sport == team.sport }
        val best = sameSport.firstOrNull { it.name.equals(team.lookup, true) || it.name.equals(team.name, true) }
            ?: sameSport.firstOrNull { it.league.contains(if (team.sport == FOOTBALL) "Premier" else "Super League", true) }
            ?: sameSport.firstOrNull()
        return best?.badge
    }

    private suspend fun throttle() {
        val wait = lastCall + 2_100 - System.currentTimeMillis()
        if (wait > 0) delay(wait)
        lastCall = System.currentTimeMillis()
    }

    private fun smallBadge(url: String?): String? =
        url?.takeIf { it.startsWith("http") }?.let { if (it.endsWith("/small")) it else "$it/small" }

    private fun parseColour(hex: String?): Long? {
        val h = hex?.trim()?.removePrefix("#") ?: return null
        if (h.length != 6) return null
        return h.toLongOrNull(16)?.let { 0xFF000000 or it }
    }

    private fun JsonObject.s(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
}
