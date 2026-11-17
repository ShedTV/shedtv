package com.matchday.tv.data

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import java.util.UUID

/** One provider login. Favourites, resume points and the guide cache are kept per subscription. */
data class Subscription(
    val id: String,
    val name: String,
    val server: String,
    val username: String,
    val password: String,
    val userAgent: String = UserAgents.DEFAULT,
    /** "ts" (MPEG-TS) or "m3u8" (HLS). */
    val streamFormat: String = "ts",
) {
    val credentials get() = Credentials(server, username, password)
    val host: String get() = server.removePrefix("http://").removePrefix("https://").substringBefore('/')
}

/** The name the app gives providers when it connects. */
object UserAgents {
    data class Preset(val label: String, val value: String)

    const val DEFAULT = "Dalvik/2.1.0 (Linux; U; Android 9; AFTKA Build/PS7633)"

    val presets = listOf(
        Preset("Android device", DEFAULT),
        Preset("VLC", "VLC/3.0.21 LibVLC/3.0.21"),
        Preset("Web browser", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"),
        Preset("Media player", "Lavf/60.16.100"),
        Preset("ShedTV", "ShedTV/0.1 (Android TV)"),
    )

    fun labelFor(value: String): String = presets.firstOrNull { it.value == value }?.label ?: "Custom"

    /** Used by every request to the provider: API, guide and streams. */
    @Volatile
    var current: String = DEFAULT
}

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("matchday", Context.MODE_PRIVATE)

    init {
        migrateSingleLogin()
    }

    var subscriptions: List<Subscription>
        get() = try {
            (Json.parseToJsonElement(sp.getString("subscriptions", "[]") ?: "[]") as JsonArray).mapNotNull { el ->
                val o = el as? JsonObject ?: return@mapNotNull null
                Subscription(
                    id = o.s("id") ?: return@mapNotNull null,
                    name = o.s("name").orEmpty().ifBlank { "Subscription" },
                    server = o.s("server") ?: return@mapNotNull null,
                    username = o.s("username").orEmpty(),
                    password = o.s("password").orEmpty(),
                    userAgent = o.s("userAgent")?.ifBlank { null } ?: UserAgents.DEFAULT,
                    streamFormat = o.s("streamFormat") ?: "ts",
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
        set(value) {
            val arr = buildJsonArray {
                value.forEach { s ->
                    add(buildJsonObject {
                        put("id", s.id); put("name", s.name); put("server", s.server)
                        put("username", s.username); put("password", s.password)
                        put("userAgent", s.userAgent); put("streamFormat", s.streamFormat)
                    })
                }
            }
            sp.edit().putString("subscriptions", arr.toString()).apply()
        }

    var mergeQualities: Boolean
        get() = sp.getBoolean("mergeQualities", false)
        set(value) = sp.edit().putBoolean("mergeQualities", value).apply()

    var preview: Boolean
        get() = sp.getBoolean("preview", true)
        set(value) = sp.edit().putBoolean("preview", value).apply()

    var stay50Hz: Boolean
        get() = sp.getBoolean("stay50Hz", false)
        set(value) = sp.edit().putBoolean("stay50Hz", value).apply()

    var animations: Boolean
        get() = sp.getBoolean("animations", true)
        set(value) = sp.edit().putBoolean("animations", value).apply()

    var liveRewind: Boolean
        get() = sp.getBoolean("liveRewind", true)
        set(value) = sp.edit().putBoolean("liveRewind", value).apply()

    var activeId: String?
        get() = sp.getString("activeSubscription", null)
        set(value) = sp.edit().putString("activeSubscription", value).apply()

    val active: Subscription? get() = subscriptions.let { list -> list.firstOrNull { it.id == activeId } ?: list.firstOrNull() }

    fun hiddenCategories(subId: String): Set<String> = sp.getStringSet("hidden_$subId", emptySet())?.toSet() ?: emptySet()
    fun setHiddenCategories(subId: String, value: Set<String>) = sp.edit().putStringSet("hidden_$subId", value).apply()

    fun favourites(subId: String): Set<String> = sp.getStringSet("favourites_$subId", emptySet())?.toSet() ?: emptySet()
    fun setFavourites(subId: String, value: Set<String>) = sp.edit().putStringSet("favourites_$subId", value).apply()

    /** Builds before multiple subscriptions kept a single login; turn it into the first subscription. */
    private fun migrateSingleLogin() {
        val server = sp.getString("server", null) ?: return
        val id = UUID.randomUUID().toString().take(8)
        val sub = Subscription(
            id = id, name = "Subscription 1", server = server,
            username = sp.getString("username", "").orEmpty(), password = sp.getString("password", "").orEmpty(),
            streamFormat = sp.getString("streamFormat", "ts") ?: "ts",
        )
        val favs = sp.getStringSet("favourites", emptySet())?.toSet() ?: emptySet()
        sp.edit()
            .remove("server").remove("username").remove("password").remove("streamFormat").remove("favourites")
            .putStringSet("favourites_$id", favs)
            .apply()
        subscriptions = subscriptions + sub
        activeId = id
    }

    private fun JsonObject.s(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull

    companion object {
        fun newId(): String = UUID.randomUUID().toString().take(8)
    }
}
