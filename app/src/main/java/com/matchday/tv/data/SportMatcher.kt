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

    private val nonWord = Regex("[^a-z0-9 ]")
    private val spaces = Regex("\\s+")
    private val quickCheck = Regex("\\sv(?:s|s\\.|ersus)?\\s", RegexOption.IGNORE_CASE)

    fun normalise(s: String): String =
        s.lowercase().replace("&", " and ").replace(nonWord, " ").replace(spaces, " ").trim()

    /** Normalised with club suffixes (FC, AFC…) dropped. */
    private fun clean(s: String): String =
        normalise(s).split(' ').filter { it.isNotEmpty() && it !in suffixes }.joinToString(" ")

    /** Look-up table from every name a team goes by to the teams using it. Build once per scan. */
    fun index(teams: List<Team>): Map<String, List<Team>> {
        val map = HashMap<String, MutableList<Team>>()
        for (t in teams) for (a in t.aliases) for (k in setOf(normalise(a), clean(a))) {
            if (k.isEmpty()) continue
            val list = map.getOrPut(k) { ArrayList() }
            if (t !in list) list.add(t)
        }
        return map
    }

    private fun candidates(side: String, index: Map<String, List<Team>>): List<Team> =
        index[normalise(side)] ?: index[clean(side)] ?: emptyList()

    /** Parse a title into a fixture, resolving teams through [index] where possible. */
    fun parse(entry: EpgEntry, channel: Channel, index: Map<String, List<Team>>): Fixture? {
        if (!quickCheck.containsMatchIn(entry.title)) return null
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
        val homeCands = candidates(home, index)
        val awayCands = candidates(away, index)
        // Pick the pairing that agrees on sport: the hinted sport first, then whichever
        // sport knows both sides ("Hull v Leeds" is rugby league if nothing says otherwise…
        // unless both are football clubs too, when the hint decides).
        val sports = (listOfNotNull(hint) + listOf(FOOTBALL, RUGBY_LEAGUE)).distinct()
        fun pair(sport: String) = homeCands.firstOrNull { it.sport == sport } to awayCands.firstOrNull { it.sport == sport }
        val chosen = sports.map { pair(it) }.firstOrNull { it.first != null && it.second != null }
            ?: sports.map { pair(it) }.firstOrNull { it.first != null || it.second != null }
        val homeTeam = chosen?.first
        val awayTeam = chosen?.second
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
