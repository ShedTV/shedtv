package com.matchday.tv.data

data class VodItem(
    val id: String,
    val name: String,
    val poster: String?,
    val rating: String?,
    val categoryId: String?,
    val ext: String,
    val added: Long,
    /** Set for films from another subscription. */
    val subId: String? = null,
)

data class SeriesItem(
    val id: String,
    val name: String,
    val cover: String?,
    val plot: String,
    val genre: String,
    val year: String,
    val rating: String?,
    val categoryId: String?,
    val updated: Long,
    /** Set for series from another subscription. */
    val subId: String? = null,
)

data class MovieInfo(
    val plot: String,
    val genre: String,
    val cast: String,
    val director: String,
    val year: String,
    val duration: String,
    val backdrop: String?,
    val rating: String?,
    val ext: String?,
)

data class Episode(
    val id: String,
    val season: Int,
    val num: Int,
    val title: String,
    val plot: String,
    val duration: String,
    val image: String?,
    val ext: String,
)

data class SeriesInfo(
    val plot: String,
    val genre: String,
    val cast: String,
    val year: String,
    val backdrop: String?,
    val seasons: Map<Int, List<Episode>>,
)

/** Something the on-demand player can play. */
data class Playable(
    /** "movie" or "episode". */
    val kind: String,
    val id: String,
    val title: String,
    val subtitle: String,
    val url: String,
    val image: String?,
    /** Series id for episodes. */
    val parentId: String?,
    val startAt: Long,
    /** Episodes to play after this one. */
    val upNext: List<Playable> = emptyList(),
    /** User agent of the subscription this comes from. */
    val userAgent: String? = null,
    /** Set when playing a downloaded copy. */
    val downloadId: String? = null,
)

data class WatchEntry(
    val kind: String,
    val id: String,
    val title: String,
    val subtitle: String,
    val image: String?,
    val parentId: String?,
    val position: Long,
    val duration: Long,
    val updated: Long,
) {
    val progress: Float get() = if (duration <= 0) 0f else (position.toFloat() / duration).coerceIn(0f, 1f)
}
