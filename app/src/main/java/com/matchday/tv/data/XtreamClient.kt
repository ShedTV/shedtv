package com.matchday.tv.data

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

const val USER_AGENT = "ShedTV/0.1 (Android TV)"

/** Talks to an Xtream Codes compatible provider. */
class XtreamClient(credentials: Credentials) {

    val creds = credentials.copy(server = normaliseServer(credentials.server))

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun apiUrl(action: String? = null, extra: String = ""): String = buildString {
        append(creds.server)
        append("/player_api.php?username=").append(enc(creds.username))
        append("&password=").append(enc(creds.password))
        if (action != null) append("&action=").append(action)
        append(extra)
    }

    private suspend fun get(url: String): JsonElement = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Provider returned HTTP ${response.code}")
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) throw IOException("Provider returned an empty response")
            try {
                json.parseToJsonElement(body)
            } catch (e: Exception) {
                throw IOException("Provider sent something unexpected. Check the server address.")
            }
        }
    }

    suspend fun login(): AccountInfo {
        val root = get(apiUrl()) as? JsonObject
            ?: throw IOException("Unexpected response. Check the server address.")
        val user = root["user_info"] as? JsonObject
            ?: throw IOException("Login failed. Check your username and password.")
        if (user.str("auth")?.toIntOrNull() != 1) {
            throw IOException("Login rejected. Check your username and password.")
        }
        val status = user.str("status")
        if (status != null && !status.equals("Active", ignoreCase = true)) {
            throw IOException("Account status: $status")
        }
        return AccountInfo(status, user.str("exp_date"), user.str("max_connections"))
    }

    suspend fun liveCategories(): List<Category> = categories("get_live_categories")
    suspend fun vodCategories(): List<Category> = categories("get_vod_categories")
    suspend fun seriesCategories(): List<Category> = categories("get_series_categories")

    private suspend fun categories(action: String): List<Category> {
        val arr = get(apiUrl(action)) as? JsonArray ?: return emptyList()
        return arr.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val id = o.str("category_id") ?: return@mapNotNull null
            Category(id, o.str("category_name")?.trim().orEmpty().ifEmpty { "Other" })
        }.distinctBy { it.id }
    }

    suspend fun liveStreams(): List<Channel> {
        val arr = get(apiUrl("get_live_streams")) as? JsonArray ?: return emptyList()
        return arr.mapIndexedNotNull { index, el ->
            val o = el as? JsonObject ?: return@mapIndexedNotNull null
            val id = o.str("stream_id") ?: return@mapIndexedNotNull null
            Channel(
                streamId = id,
                num = o.str("num")?.toIntOrNull() ?: (index + 1),
                name = o.str("name")?.trim().orEmpty().ifEmpty { "Channel $id" },
                logo = o.str("stream_icon")?.takeIf { it.startsWith("http") },
                categoryId = o.str("category_id"),
                epgId = o.str("epg_channel_id"),
                hasArchive = o.str("tv_archive")?.toIntOrNull() == 1,
            )
        }.distinctBy { it.streamId }.sortedBy { it.num }
    }

    suspend fun shortEpg(streamId: String, limit: Int = 4): List<EpgEntry> {
        val root = get(apiUrl("get_short_epg", "&stream_id=$streamId&limit=$limit")) as? JsonObject
            ?: return emptyList()
        val listings = root["epg_listings"] as? JsonArray ?: return emptyList()
        return listings.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val start = o.str("start_timestamp")?.toLongOrNull() ?: return@mapNotNull null
            val end = o.str("stop_timestamp")?.toLongOrNull() ?: return@mapNotNull null
            EpgEntry(
                title = decode(o.str("title")).ifBlank { "No information" },
                description = decode(o.str("description")),
                start = start * 1000,
                end = end * 1000,
            )
        }
    }

    suspend fun vodStreams(): List<VodItem> {
        val arr = get(apiUrl("get_vod_streams")) as? JsonArray ?: return emptyList()
        return arr.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val id = o.str("stream_id") ?: return@mapNotNull null
            VodItem(
                id = id,
                name = o.str("name")?.trim().orEmpty().ifEmpty { "Untitled" },
                poster = o.str("stream_icon")?.takeIf { it.startsWith("http") },
                rating = o.str("rating")?.takeIf { it.isNotBlank() && it != "0" },
                categoryId = o.str("category_id"),
                ext = o.str("container_extension")?.ifBlank { null } ?: "mp4",
                added = o.str("added")?.toLongOrNull() ?: 0,
            )
        }.distinctBy { it.id }
    }

    suspend fun series(): List<SeriesItem> {
        val arr = get(apiUrl("get_series")) as? JsonArray ?: return emptyList()
        return arr.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val id = o.str("series_id") ?: return@mapNotNull null
            SeriesItem(
                id = id,
                name = o.str("name")?.trim().orEmpty().ifEmpty { "Untitled" },
                cover = o.str("cover")?.takeIf { it.startsWith("http") },
                plot = o.str("plot").orEmpty(),
                genre = o.str("genre").orEmpty(),
                year = (o.str("releaseDate") ?: o.str("release_date")).orEmpty().take(4),
                rating = o.str("rating")?.takeIf { it.isNotBlank() && it != "0" },
                categoryId = o.str("category_id"),
                updated = o.str("last_modified")?.toLongOrNull() ?: 0,
            )
        }.distinctBy { it.id }
    }

    suspend fun vodInfo(id: String): MovieInfo {
        val root = get(apiUrl("get_vod_info", "&vod_id=$id")) as? JsonObject ?: throw IOException("No details for this film")
        val info = root["info"] as? JsonObject ?: JsonObject(emptyMap())
        val data = root["movie_data"] as? JsonObject
        return MovieInfo(
            plot = (info.str("plot") ?: info.str("description")).orEmpty(),
            genre = info.str("genre").orEmpty(),
            cast = (info.str("cast") ?: info.str("actors")).orEmpty(),
            director = info.str("director").orEmpty(),
            year = (info.str("releasedate") ?: info.str("release_date")).orEmpty().take(4),
            duration = info.str("duration").orEmpty(),
            backdrop = firstUrl(info["backdrop_path"]),
            rating = info.str("rating")?.takeIf { it.isNotBlank() && it != "0" },
            ext = data?.str("container_extension"),
        )
    }

    suspend fun seriesInfo(id: String): SeriesInfo {
        val root = get(apiUrl("get_series_info", "&series_id=$id")) as? JsonObject ?: throw IOException("No details for this series")
        val info = root["info"] as? JsonObject ?: JsonObject(emptyMap())
        val episodes = ArrayList<Episode>()
        fun addFrom(arr: JsonArray, seasonKey: Int?) {
            arr.forEach { el ->
                val o = el as? JsonObject ?: return@forEach
                val epId = o.str("id") ?: return@forEach
                val epInfo = o["info"] as? JsonObject
                episodes += Episode(
                    id = epId,
                    season = o.str("season")?.toIntOrNull() ?: seasonKey ?: 1,
                    num = o.str("episode_num")?.toIntOrNull() ?: (episodes.size + 1),
                    title = o.str("title")?.trim().orEmpty(),
                    plot = epInfo?.str("plot").orEmpty(),
                    duration = epInfo?.str("duration").orEmpty(),
                    image = epInfo?.str("movie_image")?.takeIf { it.startsWith("http") },
                    ext = o.str("container_extension")?.ifBlank { null } ?: "mp4",
                )
            }
        }
        when (val eps = root["episodes"]) {
            is JsonObject -> eps.forEach { (k, v) -> (v as? JsonArray)?.let { addFrom(it, k.toIntOrNull()) } }
            is JsonArray -> eps.forEach { (it as? JsonArray)?.let { arr -> addFrom(arr, null) } }
            else -> Unit
        }
        return SeriesInfo(
            plot = info.str("plot").orEmpty(),
            genre = info.str("genre").orEmpty(),
            cast = info.str("cast").orEmpty(),
            year = (info.str("releaseDate") ?: info.str("release_date")).orEmpty().take(4),
            backdrop = firstUrl(info["backdrop_path"]) ?: info.str("cover")?.takeIf { it.startsWith("http") },
            seasons = episodes.groupBy { it.season }.toSortedMap().mapValues { (_, l) -> l.sortedBy { it.num } },
        )
    }

    fun movieUrl(id: String, ext: String) =
        "${creds.server}/movie/${encPath(creds.username)}/${encPath(creds.password)}/$id.$ext"

    fun episodeUrl(id: String, ext: String) =
        "${creds.server}/series/${encPath(creds.username)}/${encPath(creds.password)}/$id.$ext"

    private fun firstUrl(el: JsonElement?): String? = when (el) {
        is JsonArray -> el.firstNotNullOfOrNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf { u -> u.startsWith("http") } }
        is JsonPrimitive -> el.contentOrNull?.takeIf { it.startsWith("http") }
        else -> null
    }

    fun xmltvUrl(): String =
        "${creds.server}/xmltv.php?username=${enc(creds.username)}&password=${enc(creds.password)}"

    fun streamUrl(channel: Channel, format: String): String {
        val ext = if (format == "m3u8") "m3u8" else "ts"
        return "${creds.server}/live/${encPath(creds.username)}/${encPath(creds.password)}/${channel.streamId}.$ext"
    }

    companion object {
        val http: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        fun normaliseServer(raw: String): String {
            var s = raw.trim()
            if (!s.startsWith("http://") && !s.startsWith("https://")) s = "http://$s"
            s = s.substringBefore("/player_api.php").substringBefore("/get.php")
            return s.trimEnd('/')
        }

        private val BASE64 = Regex("^[A-Za-z0-9+/\\r\\n]+={0,2}$")

        private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
        private fun encPath(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

        private fun decode(s: String?): String {
            if (s.isNullOrBlank()) return ""
            // Most providers base64-encode EPG text; some send it plain.
            if (!BASE64.matches(s.trim())) return s.trim()
            return try {
                val out = String(Base64.decode(s, Base64.DEFAULT), Charsets.UTF_8).trim()
                if (out.any { it == '�' || (it.isISOControl() && it != '\n') }) s.trim() else out
            } catch (e: IllegalArgumentException) {
                s.trim()
            }
        }
    }
}

private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
