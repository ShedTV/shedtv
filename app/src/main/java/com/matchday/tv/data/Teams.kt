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

private fun efl(id: String, name: String, short: String, league: String, colour: Long, text: Long, vararg aliases: String) =
    Team("fb-$id", name, short, FOOTBALL, league, listOf(name) + aliases, colour, text)

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

    val championship = League(
        "ch", "Championship 2026/27", FOOTBALL, listOf(
            efl("birmingham", "Birmingham City", "BIR", "Championship", 0xFF0000FF, 0xFFFFFFFF, "Birmingham"),
            efl("blackburn", "Blackburn Rovers", "BLB", "Championship", 0xFF009EE0, 0xFFFFFFFF, "Blackburn"),
            efl("bolton", "Bolton Wanderers", "BOL", "Championship", 0xFFF2F2F2, 0xFF1C2C5B, "Bolton"),
            efl("bristolcity", "Bristol City", "BRC", "Championship", 0xFFE21A23, 0xFFFFFFFF),
            efl("burnley", "Burnley", "BUR", "Championship", 0xFF6C1D45, 0xFF99D6EA),
            efl("cardiff", "Cardiff City", "CAR", "Championship", 0xFF0070B5, 0xFFFFFFFF, "Cardiff"),
            efl("charlton", "Charlton Athletic", "CHA", "Championship", 0xFFD4021D, 0xFFFFFFFF, "Charlton"),
            efl("derby", "Derby County", "DER", "Championship", 0xFFF2F2F2, 0xFF000000, "Derby"),
            efl("lincoln", "Lincoln City", "LIN", "Championship", 0xFFD71920, 0xFFFFFFFF, "Lincoln"),
            efl("boro", "Middlesbrough", "MID", "Championship", 0xFFE11B22, 0xFFFFFFFF, "Boro"),
            efl("millwall", "Millwall", "MIL", "Championship", 0xFF001E50, 0xFFFFFFFF),
            efl("norwich", "Norwich City", "NOR", "Championship", 0xFFFFF200, 0xFF00A650, "Norwich"),
            efl("portsmouth", "Portsmouth", "POR", "Championship", 0xFF001489, 0xFFFFFFFF, "Pompey"),
            efl("preston", "Preston North End", "PNE", "Championship", 0xFFF2F2F2, 0xFF001489, "Preston"),
            efl("qpr", "Queens Park Rangers", "QPR", "Championship", 0xFF1D5BA4, 0xFFFFFFFF, "QPR"),
            efl("sheffutd", "Sheffield United", "SHU", "Championship", 0xFFEE2737, 0xFFFFFFFF, "Sheff Utd", "Sheffield Utd"),
            efl("southampton", "Southampton", "SOU", "Championship", 0xFFD71920, 0xFFFFFFFF, "Saints FC"),
            efl("stoke", "Stoke City", "STK", "Championship", 0xFFE03A3E, 0xFFFFFFFF, "Stoke"),
            efl("swansea", "Swansea City", "SWA", "Championship", 0xFFF2F2F2, 0xFF000000, "Swansea"),
            efl("watford", "Watford", "WAT", "Championship", 0xFFFBEE23, 0xFF11210C),
            efl("westbrom", "West Bromwich Albion", "WBA", "Championship", 0xFF122F67, 0xFFFFFFFF, "West Brom", "WBA"),
            efl("westham", "West Ham United", "WHU", "Championship", 0xFF7A263A, 0xFF1BB1E7, "West Ham"),
            efl("wolves", "Wolverhampton Wanderers", "WOL", "Championship", 0xFFFDB913, 0xFF231F20, "Wolves"),
            efl("wrexham", "Wrexham", "WRE", "Championship", 0xFFE30613, 0xFFFFFFFF, "Wrexham AFC"),
        )
    )

    val leagueOne = League(
        "l1", "League One 2026/27", FOOTBALL, listOf(
            efl("wimbledon", "AFC Wimbledon", "WIM", "League One", 0xFF0E2B8C, 0xFFF5D130, "Wimbledon"),
            efl("barnsley", "Barnsley", "BAR", "League One", 0xFFD71920, 0xFFFFFFFF),
            efl("blackpool", "Blackpool", "BPL", "League One", 0xFFF68712, 0xFFFFFFFF),
            efl("bradfordcity", "Bradford City", "BFD", "League One", 0xFF7A1E3C, 0xFFF5B700),
            efl("bromley", "Bromley", "BRO", "League One", 0xFFF2F2F2, 0xFF000000),
            efl("burton", "Burton Albion", "BRT", "League One", 0xFFFFD100, 0xFF000000, "Burton"),
            efl("cambridge", "Cambridge United", "CAM", "League One", 0xFFFDB913, 0xFF000000, "Cambridge Utd", "Cambridge"),
            efl("doncaster", "Doncaster Rovers", "DON", "League One", 0xFFE21A23, 0xFFFFFFFF, "Doncaster"),
            efl("huddtown", "Huddersfield Town", "HUD", "League One", 0xFF0E63AD, 0xFFFFFFFF, "Huddersfield Town AFC"),
            efl("leicester", "Leicester City", "LEI", "League One", 0xFF003090, 0xFFFDBE11, "Leicester"),
            efl("orient", "Leyton Orient", "LEY", "League One", 0xFFD71920, 0xFFFFFFFF, "Orient"),
            efl("luton", "Luton Town", "LUT", "League One", 0xFFF78F1E, 0xFF002D62, "Luton"),
            efl("mansfield", "Mansfield Town", "MAN", "League One", 0xFFF5A300, 0xFF003DA5, "Mansfield"),
            efl("mkdons", "Milton Keynes Dons", "MKD", "League One", 0xFFF2F2F2, 0xFF000000, "MK Dons"),
            efl("nottscounty", "Notts County", "NCO", "League One", 0xFF000000, 0xFFFFFFFF),
            efl("oxford", "Oxford United", "OXF", "League One", 0xFFFFD100, 0xFF001E50, "Oxford Utd", "Oxford"),
            efl("peterborough", "Peterborough United", "PET", "League One", 0xFF0055A4, 0xFFFFFFFF, "Peterborough", "Posh"),
            efl("plymouth", "Plymouth Argyle", "PLY", "League One", 0xFF00483A, 0xFFFFFFFF, "Plymouth"),
            efl("reading", "Reading", "REA", "League One", 0xFF004494, 0xFFFFFFFF),
            efl("sheffwed", "Sheffield Wednesday", "SHW", "League One", 0xFF0E4C92, 0xFFFFFFFF, "Sheff Wed", "Sheffield Wed"),
            efl("stevenage", "Stevenage", "STE", "League One", 0xFFF2F2F2, 0xFFE30613),
            efl("stockport", "Stockport County", "STO", "League One", 0xFF0047AB, 0xFFFFFFFF, "Stockport"),
            efl("wiganath", "Wigan Athletic", "WGA", "League One", 0xFF1D59AF, 0xFFFFFFFF, "Wigan Ath"),
            efl("wycombe", "Wycombe Wanderers", "WYC", "League One", 0xFF7EB6E5, 0xFF001E50, "Wycombe"),
        )
    )

    val leagueTwo = League(
        "l2", "League Two 2026/27", FOOTBALL, listOf(
            efl("accrington", "Accrington Stanley", "ACC", "League Two", 0xFFE30613, 0xFFFFFFFF, "Accrington"),
            efl("barnet", "Barnet", "BNT", "League Two", 0xFFF5A300, 0xFF000000),
            efl("bristolrovers", "Bristol Rovers", "BRR", "League Two", 0xFF00539F, 0xFFFFFFFF),
            efl("cheltenham", "Cheltenham Town", "CHL", "League Two", 0xFFE30613, 0xFFFFFFFF, "Cheltenham"),
            efl("chesterfield", "Chesterfield", "CHF", "League Two", 0xFF0047AB, 0xFFFFFFFF),
            efl("colchester", "Colchester United", "COL", "League Two", 0xFF0055A4, 0xFFFFFFFF, "Colchester Utd", "Colchester"),
            efl("crawley", "Crawley Town", "CRA", "League Two", 0xFFD71920, 0xFFFFFFFF, "Crawley"),
            efl("crewe", "Crewe Alexandra", "CRE", "League Two", 0xFFE30613, 0xFFFFFFFF, "Crewe"),
            efl("exeter", "Exeter City", "EXE", "League Two", 0xFFE30613, 0xFFFFFFFF, "Exeter"),
            efl("fleetwood", "Fleetwood Town", "FLE", "League Two", 0xFFE30613, 0xFFFFFFFF, "Fleetwood"),
            efl("gillingham", "Gillingham", "GIL", "League Two", 0xFF0047AB, 0xFFFFFFFF),
            efl("grimsby", "Grimsby Town", "GRI", "League Two", 0xFF000000, 0xFFFFFFFF, "Grimsby"),
            efl("newport", "Newport County", "NPT", "League Two", 0xFFF5A300, 0xFF000000, "Newport"),
            efl("northampton", "Northampton Town", "NTN", "League Two", 0xFF7A1E3C, 0xFFFFFFFF, "Northampton"),
            efl("oldham", "Oldham Athletic", "OLD", "League Two", 0xFF0055A4, 0xFFFFFFFF, "Oldham"),
            efl("portvale", "Port Vale", "PVA", "League Two", 0xFFF2F2F2, 0xFF000000),
            efl("rochdale", "Rochdale", "ROC", "League Two", 0xFF0047AB, 0xFFFFFFFF, "Rochdale AFC"),
            efl("rotherham", "Rotherham United", "ROT", "League Two", 0xFFE30613, 0xFFFFFFFF, "Rotherham Utd", "Rotherham"),
            efl("salford", "Salford City", "SAL", "League Two", 0xFFE30613, 0xFFFFFFFF, "Salford"),
            efl("shrewsbury", "Shrewsbury Town", "SHR", "League Two", 0xFF0055A4, 0xFFF5A300, "Shrewsbury"),
            efl("swindon", "Swindon Town", "SWI", "League Two", 0xFFE30613, 0xFFFFFFFF, "Swindon"),
            efl("tranmere", "Tranmere Rovers", "TRA", "League Two", 0xFFF2F2F2, 0xFF0055A4, "Tranmere"),
            efl("walsall", "Walsall", "WAL", "League Two", 0xFFE30613, 0xFFFFFFFF),
            efl("yorkcity", "York City", "YKC", "League Two", 0xFF0055A4, 0xFFFFFFFF),
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

    val leagues = listOf(premierLeague, championship, leagueOne, leagueTwo, superLeague, internationals)
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
