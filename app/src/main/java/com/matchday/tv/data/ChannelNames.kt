package com.matchday.tv.data

/** Recognises quality versions of the same channel ("Sky Sports Main Event | UHD", "… FHD", "… HD"). */
object ChannelNames {
    private val tags = setOf(
        "UHD", "4K", "FHD", "HD", "SD", "HEVC", "H265", "H.265", "H264", "1080P", "720P", "50FPS", "60FPS",
        "RAW", "BACKUP", "HDR", "ᵁᴴᴰ", "ᶠᴴᴰ", "ᴴᴰ", "ˢᴰ", "4K⁶⁰ᶠᵖˢ",
    )
    private val separators = Regex("[\\s|]+")
    private val bracketed = Regex("[\\[(][^\\])]*[\\])]")
    private val nonWord = Regex("[^A-Z0-9+ ]")

    private fun words(name: String): List<String> {
        val noTagBrackets = bracketed.replace(name) { m ->
            val inner = m.value.uppercase().trim('[', ']', '(', ')').split(separators)
            if (inner.isNotEmpty() && inner.all { it.isBlank() || it in tags }) " " else m.value
        }
        return noTagBrackets.split(separators).filter { w -> w.isNotBlank() && w.uppercase().trim('-', '_', '.', ':') !in tags }
    }

    /** The name without quality tags, for display. */
    fun clean(name: String): String =
        words(name).joinToString(" ").trim(' ', '-', ':', '_').ifBlank { name.trim() }

    /** Two channels with the same key are versions of the same channel. */
    fun key(name: String): String =
        words(name).joinToString(" ").uppercase().replace(nonWord, "").replace(Regex(" +"), " ").trim()

    fun quality(name: String): String {
        val up = name.uppercase()
        return when {
            "UHD" in up || "4K" in up || "ᵁᴴᴰ" in name -> "UHD"
            "FHD" in up || "1080" in up || "ᶠᴴᴰ" in name -> "FHD"
            Regex("(^|[^A-Z])HD([^A-Z]|$)").containsMatchIn(up) || "ᴴᴰ" in name -> "HD"
            Regex("(^|[^A-Z])SD([^A-Z]|$)").containsMatchIn(up) -> "SD"
            else -> ""
        }
    }
}
