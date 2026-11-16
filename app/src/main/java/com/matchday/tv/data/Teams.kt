package com.matchday.tv.data

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.put

const val FOOTBALL = "football"
const val RUGBY_LEAGUE = "rugby"

data class Team(
    /** Stable id, e.g. "fb-arsenal" or "tsdb-133604". */
    val key: String,
    val name: String,
    /** Up to 3 letters for the fallback badge. */
    val short: String,
    val sport: String,
    val league: String,
    /** Names the TV guide might use, matched as whole words. */
    val aliases: List<String>,
    val colour: Long,
    val textColour: Long,
    /** Crest image URL once known. */
    val badge: String? = null,
    /** Name to look up on TheSportsDB for the crest. */
    val lookup: String = name,
)

data class League(val id: String, val name: String, val sport: String, val teams: List<Team>)

private fun fb(id: String, name: String, short: String, colour: Long, text: Long, vararg aliases: String, lookup: String = name) =
    Team("fb-$id", name, short, FOOTBALL, "Premier League", listOf(name) + aliases, colour, text, lookup = lookup)

private fun rl(id: String, name: String, short: String, colour: Long, text: Long, vararg aliases: String, lookup: String = name) =
    Team("rl-$id", name, short, RUGBY_LEAGUE, "Super League", listOf(name) + aliases, colour, text, lookup = lookup)

private fun intl(id: String, name: String, short: String, sport: String, colour: Long, text: Long, aliases: List<String>, lookup: String) =
    Team("int-$id", name, short, sport, "Internationals", aliases, colour, text, lookup = lookup)

/** Built-in teams. Anything missing can be found with Search in the picker. */
object TeamsCatalog {
    val premierLeague = League(
        "pl", "Premier League 2026/27", FOOTBALL, listOf(
            fb("arsenal", "Arsenal", "ARS", 0xFFDB0007, 0xFFFFFFFF),
            fb("villa", "Aston Villa", "AVL", 0xFF670E36, 0xFF95BFE5, "Villa"),
            fb("bournemouth", "Bournemouth", "BOU", 0xFFDA291C, 0xFF000000, "AFC Bournemouth"),
            fb("brentford", "Brentford", "BRE", 0xFFE30613, 0xFFFFFFFF),
            fb("brighton", "Brighton", "BHA", 0xFF0057B8, 0xFFFFFFFF, "Brighton and Hove Albion", "Brighton & Hove Albion", lookup = "Brighton and Hove Albion"),
            fb("chelsea", "Chelsea", "CHE", 0xFF034694, 0xFFFFFFFF),
            fb("coventry", "Coventry City", "COV", 0xFF59CBE8, 0xFF0B1F3A, "Coventry"),
            fb("palace", "Crystal Palace", "CRY", 0xFF1B458F, 0xFFC4122E, "Palace"),
            fb("everton", "Everton", "EVE", 0xFF003399, 0xFFFFFFFF),
            fb("fulham", "Fulham", "FUL", 0xFFF2F2F2, 0xFF000000),
            fb("hullcity", "Hull City", "HUL", 0xFFF5A12D, 0xFF000000, "Hull City AFC"),
            fb("ipswich", "Ipswich Town", "IPS", 0xFF0044A9, 0xFFFFFFFF, "Ipswich"),
            fb("leeds", "Leeds United", "LEE", 0xFFF2F2F2, 0xFF1D428A, "Leeds Utd", "Leeds"),
            fb("liverpool", "Liverpool", "LIV", 0xFFC8102E, 0xFFFFFFFF),
            fb("mancity", "Manchester City", "MCI", 0xFF6CABDD, 0xFF1C2C5B, "Man City"),
            fb("manutd", "Manchester United", "MUN", 0xFFDA291C, 0xFFFBE122, "Man Utd", "Man United", "Manchester Utd"),
            fb("newcastle", "Newcastle United", "NEW", 0xFF241F20, 0xFFFFFFFF, "Newcastle", "Newcastle Utd"),
            fb("forest", "Nottingham Forest", "NFO", 0xFFDD0000, 0xFFFFFFFF, "Nottm Forest", "Nott'm Forest", "Forest"),
            fb("sunderland", "Sunderland", "SUN", 0xFFEB172B, 0xFFFFFFFF),
            fb("spurs", "Tottenham Hotspur", "TOT", 0xFFF2F2F2, 0xFF132257, "Tottenham", "Spurs"),
        )
    )

    val superLeague = League(
        "sl", "Super League 2026", RUGBY_LEAGUE, listOf(
            rl("bradford", "Bradford Bulls", "BRA", 0xFF1A1A1A, 0xFFE31B23, "Bradford"),
            rl("castleford", "Castleford Tigers", "CAS", 0xFFF7A600, 0xFF000000, "Castleford"),
            rl("catalans", "Catalans Dragons", "CAT", 0xFFE30613, 0xFFFFD100, "Catalans"),
            rl("huddersfield", "Huddersfield Giants", "HUD", 0xFF7A1E3C, 0xFFF2A900, "Huddersfield"),
            rl("hullfc", "Hull FC", "HFC", 0xFF000000, 0xFFFFFFFF, "Hull F.C."),
            rl("hullkr", "Hull KR", "HKR", 0xFFE30613, 0xFFFFFFFF, "Hull Kingston Rovers", "Hull K.R.", lookup = "Hull Kingston Rovers"),
            rl("leeds", "Leeds Rhinos", "LEE", 0xFF00305E, 0xFFF5C400, "Leeds"),
            rl("leigh", "Leigh Leopards", "LEI", 0xFFE30613, 0xFFFFFFFF, "Leigh"),
            rl("sthelens", "St Helens", "STH", 0xFFF2F2F2, 0xFFE30613, "St. Helens", "Saints"),
            rl("toulouse", "Toulouse Olympique", "TOU", 0xFF4B2C82, 0xFFFFFFFF, "Toulouse"),
            rl("wakefield", "Wakefield Trinity", "WAK", 0xFF003DA5, 0xFFE30613, "Wakefield"),
            rl("warrington", "Warrington Wolves", "WAR", 0xFF002F6C, 0xFFF5C400, "Warrington"),
            rl("wigan", "Wigan Warriors", "WIG", 0xFFC8102E, 0xFFFFFFFF, "Wigan"),
            rl("york", "York Knights", "YOR", 0xFF000000, 0xFFF5C400, "York"),
        )
    )

    val internationals = League(
        "int", "Internationals", FOOTBALL, listOf(
            intl("eng-fb", "England (football)", "ENG", FOOTBALL, 0xFFF2F2F2, 0xFF1D2D5C, listOf("England"), "England"),
            intl("sco-fb", "Scotland (football)", "SCO", FOOTBALL, 0xFF0E1E5B, 0xFFFFFFFF, listOf("Scotland"), "Scotland"),
            intl("wal-fb", "Wales (football)", "WAL", FOOTBALL, 0xFFC8102E, 0xFFFFFFFF, listOf("Wales"), "Wales"),
            intl("ni-fb", "Northern Ireland", "NIR", FOOTBALL, 0xFF00843D, 0xFFFFFFFF, listOf("Northern Ireland", "N Ireland"), "Northern Ireland"),
            intl("ire-fb", "Republic of Ireland", "IRL", FOOTBALL, 0xFF169B62, 0xFFFFFFFF, listOf("Republic of Ireland", "Rep of Ireland", "Ireland"), "Republic of Ireland"),
            intl("eng-rl", "England (rugby league)", "ENG", RUGBY_LEAGUE, 0xFFF2F2F2, 0xFFC8102E, listOf("England"), "England Rugby League"),
        )
    )

    val leagues = listOf(premierLeague, superLeague, internationals)
    val all: List<Team> = leagues.flatMap { it.teams }
}

/** The teams the user follows, kept on the device. */
class FollowedTeams(context: Context) {
    private val sp = context.getSharedPreferences("shedtv_teams", Context.MODE_PRIVATE)

    fun load(): List<Team> = try {
        Json.parseToJsonElement(sp.getString("followed", "[]") ?: "[]").jsonArray.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            Team(
                key = o.s("key") ?: return@mapNotNull null,
                name = o.s("name").orEmpty(),
                short = o.s("short").orEmpty(),
                sport = o.s("sport") ?: FOOTBALL,
                league = o.s("league").orEmpty(),
                aliases = (o["aliases"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty(),
                colour = o.s("colour")?.toLongOrNull() ?: 0xFF16202A,
                textColour = o.s("textColour")?.toLongOrNull() ?: 0xFFFFFFFF,
                badge = o.s("badge"),
                lookup = o.s("lookup") ?: o.s("name").orEmpty(),
            )
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun save(teams: List<Team>) {
        val arr = buildJsonArray {
            teams.forEach { t ->
                add(buildJsonObject {
                    put("key", t.key); put("name", t.name); put("short", t.short); put("sport", t.sport)
                    put("league", t.league); put("colour", t.colour.toString()); put("textColour", t.textColour.toString())
                    put("lookup", t.lookup)
                    t.badge?.let { put("badge", it) }
                    put("aliases", buildJsonArray { t.aliases.forEach { add(JsonPrimitive(it)) } })
                })
            }
        }
        sp.edit().putString("followed", arr.toString()).apply()
    }

    /** Crest URLs found so far, by team key. */
    fun badges(): Map<String, String> = try {
        (Json.parseToJsonElement(sp.getString("badges", "{}") ?: "{}") as JsonObject)
            .mapNotNull { (k, v) -> (v as? JsonPrimitive)?.contentOrNull?.let { k to it } }.toMap()
    } catch (e: Exception) {
        emptyMap()
    }

    fun saveBadges(map: Map<String, String>) {
        sp.edit().putString("badges", buildJsonObject { map.forEach { (k, v) -> put(k, v) } }.toString()).apply()
    }

    private fun JsonObject.s(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull
}
