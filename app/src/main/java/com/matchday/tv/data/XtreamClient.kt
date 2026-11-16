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

const val USER_AGENT = "Matchday/0.1 (Android TV)"

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

    suspend fun liveCategories(): List<Category> {
        val arr = get(apiUrl("get_live_categories")) as? JsonArray ?: return emptyList()
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
