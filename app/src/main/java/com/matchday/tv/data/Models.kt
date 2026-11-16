package com.matchday.tv.data

data class Credentials(
    val server: String,
    val username: String,
    val password: String,
)

data class AccountInfo(
    val status: String?,
    val expiry: String?,
    val maxConnections: String?,
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
)

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
