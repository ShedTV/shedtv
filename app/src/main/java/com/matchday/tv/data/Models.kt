package com.matchday.tv.data

data class Credentials(
    val server: String,
    val username: String,
    val password: String,
    /** An M3U playlist: [server] is the playlist's address; no username or password. */
    val playlist: Boolean = false,
    /** Guide (XMLTV) address for a playlist; blank uses the playlist's own, if it names one. */
    val epgUrl: String = "",
)

data class AccountInfo(
    val status: String?,
    val expiry: String?,
    val maxConnections: String?,
    /** The provider's clock offset from UTC, worked out from its reported local time. */
    val serverOffsetMs: Long? = null,
    /** The provider's time zone name, if it gives one. */
    val timezone: String? = null,
)

data class Category(
    val id: String,
    val name: String,
)

data class Channel(
    val streamId: String,
    val num: Int,
    val name: String,
    val logo: String?,
    val categoryId: String?,
    val epgId: String?,
    val hasArchive: Boolean,
    /** Set for channels from another (backup) subscription; null for the active one. */
    val subId: String? = null,
    /** How many days of catch-up the provider keeps for this channel. */
    val archiveDays: Int = 0,
    /** Playlist channels: the stream's own address. */
    val directUrl: String? = null,
) {
    /** Unique across subscriptions (two providers can use the same stream number). */
    val uid: String get() = (subId ?: "") + ":" + streamId
}

data class EpgEntry(
    val title: String,
    val description: String,
    /** Epoch milliseconds. */
    val start: Long,
    /** Epoch milliseconds. */
    val end: Long,
) {
    fun isOn(now: Long) = now in start until end
    fun progress(now: Long): Float =
        if (end <= start) 0f else ((now - start).toFloat() / (end - start)).coerceIn(0f, 1f)
}

/** The programme on now and the one after, from a channel's short EPG. */
fun List<EpgEntry>.nowAndNext(now: Long = System.currentTimeMillis()): Pair<EpgEntry?, EpgEntry?> {
    val sorted = sortedBy { it.start }
    val current = sorted.firstOrNull { it.isOn(now) }
    val next = sorted.firstOrNull { it.start >= (current?.end ?: now) }
    return current to next
}
