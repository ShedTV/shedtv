package com.matchday.tv.data

/** A match found in the TV guide. */
data class Fixture(
    val home: String,
    val away: String,
    val homeTeam: Team?,
    val awayTeam: Team?,
    val competition: String,
    val live: Boolean,
    val entry: EpgEntry,
    val channel: Channel,
    /** Other channels showing the same programme (backup streams). */
    val alternatives: List<Channel> = emptyList(),
) {
    val title: String get() = "$home v $away"
    fun involves(keys: Set<String>) = homeTeam?.key in keys || awayTeam?.key in keys
}

/** Finds football and rugby league fixtures in programme titles like "Live Super League: Hull KR v Wigan". */
object SportMatcher {

    private val split = Regex("\\s+(?:v|vs|vs\\.|versus)\\s+", RegexOption.IGNORE_CASE)
    private val livePrefix = Regex("^(?:live|live:)\\s*", RegexOption.IGNORE_CASE)
    private val bracketed = Regex("\\s*[(\\[].*?[)\\]]")
    private val suffixes = setOf("fc", "afc", "rlfc", "rl", "rufc", "the")
    private val rugbyHints = listOf("rugby league", "super league", "nrl", "challenge cup", "rlwc", "betfred")
    private val footballHints = listOf("football", "premier league", "efl", "fa cup", "carabao", "champions league", "europa", "spfl", "soccer", "skybet", "sky bet", "women's super league", "wsl")

    fun normalise(s: String): String =
        s.lowercase().replace("&", " and ").replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()

    private fun sideMatches(side: String, team: Team): Boolean {
        val words = normalise(side).split(' ').filter { it.isNotEmpty() && it !in suffixes }.joinToString(" ")
        return team.aliases.any { normalise(it) == words }
    }

    /** Parse a title into a fixture, resolving teams from [teams] where possible. */
    fun parse(entry: EpgEntry, channel: Channel, teams: List<Team>): Fixture? {
        val raw = entry.title.trim()
        val stripped = raw.replace(livePrefix, "")
        val competition = stripped.substringBeforeLast(':', "").trim()
        val body = stripped.substringAfterLast(':').replace(bracketed, "").trim()
        val parts = body.split(split)
        if (parts.size != 2) return null
        val home = parts[0].trim().trimEnd('-', ',', '.').trim()
        val away = parts[1].substringBefore(" - ").trim().trimEnd('-', ',', '.').trim()
        if (home.length < 2 || away.length < 2 || home.length > 40 || away.length > 40) return null

        val context = normalise(raw + " " + entry.description + " " + channel.name)
        val hint = when {
            rugbyHints.any { context.contains(it) } -> RUGBY_LEAGUE
            footballHints.any { context.contains(it) } -> FOOTBALL
            else -> null
        }
        val homeCands = teams.filter { sideMatches(home, it) }
        val awayCands = teams.filter { sideMatches(away, it) }
        // Pick the pairing that agrees on sport (and with any hint from the title or channel).
        var homeTeam: Team? = null
        var awayTeam: Team? = null
        val sports = listOfNotNull(hint) + listOf(FOOTBALL, RUGBY_LEAGUE)
        for (sport in sports) {
            val h = homeCands.firstOrNull { it.sport == sport }
            val a = awayCands.firstOrNull { it.sport == sport }
            if (h != null || a != null) {
                if (hint != null && sport != hint && (homeCands.any { it.sport == hint } || awayCands.any { it.sport == hint })) continue
                homeTeam = h; awayTeam = a
                break
            }
        }
        val live = raw.contains("live", ignoreCase = true) || entry.description.startsWith("live", ignoreCase = true)
        val looksSporty = homeTeam != null || awayTeam != null || hint != null || channel.name.contains("sport", true)
        if (!looksSporty) return null
        return Fixture(home, away, homeTeam, awayTeam, competition, live, entry, channel)
    }

    /** Prefer favourites, then higher-quality feeds, then lower channel numbers. */
    fun channelScore(c: Channel, favourites: Set<String>): Int {
        val n = c.name.uppercase()
        var s = 0
        if (c.streamId in favourites) s += 100
        if ("UHD" in n || "4K" in n) s += 30 else if ("FHD" in n || "1080" in n) s += 25 else if ("HD" in n) s += 20
        if ("SD" in n.split(' ', '|', '-')) s -= 10
        if ("BACKUP" in n || "RAW" in n) s -= 5
        return s
    }
}
