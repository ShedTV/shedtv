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
    /** True for an M3U playlist (server holds the playlist address). */
    val playlist: Boolean = false,
    val epgUrl: String = "",
) {
    val credentials get() = Credentials(server, username, password, playlist, epgUrl)
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
                    playlist = o.s("playlist") == "true",
                    epgUrl = o.s("epgUrl").orEmpty(),
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
                        put("playlist", s.playlist.toString()); put("epgUrl", s.epgUrl)
                    })
                }
            }
            sp.edit().putString("subscriptions", arr.toString()).apply()
        }

    var mergeQualities: Boolean
        get() = sp.getBoolean("mergeQualities", false)
        set(value) = sp.edit().putBoolean("mergeQualities", value).apply()

    var menuHidden: Set<String>
        get() = sp.getStringSet("menuHidden", emptySet())?.toSet() ?: emptySet()
        set(value) = sp.edit().putStringSet("menuHidden", value).apply()

    var menuOrder: List<String>
        get() = sp.getString("menuOrder", "")?.split(',')?.filter { it.isNotBlank() } ?: emptyList()
        set(value) = sp.edit().putString("menuOrder", value.joinToString(",")).apply()

    /** home, live, guide or last. */
    var startScreen: String
        get() = sp.getString("startScreen", "home") ?: "home"
        set(value) = sp.edit().putString("startScreen", value).apply()

    var lastScreen: String?
        get() = sp.getString("lastScreen", null)
        set(value) = sp.edit().putString("lastScreen", value).apply()

    /** 1.0 normal, 1.15 large, 1.3 extra large. */
    var textScale: Float
        get() = sp.getFloat("textScale", 1f)
        set(value) = sp.edit().putFloat("textScale", value).apply()

    var highContrast: Boolean
        get() = sp.getBoolean("highContrast", false)
        set(value) = sp.edit().putBoolean("highContrast", value).apply()

    var accent: Long
        get() = sp.getLong("accent", 0xFF44C470)
        set(value) = sp.edit().putLong("accent", value).apply()

    /** compact, normal or large. */
    var guideRows: String
        get() = sp.getString("guideRows", "normal") ?: "normal"
        set(value) = sp.edit().putString("guideRows", value).apply()

    /** "play": OK goes full screen; "preview": first OK previews, second goes full screen. */
    var okAction: String
        get() = sp.getString("okAction", "play") ?: "play"
        set(value) = sp.edit().putString("okAction", value).apply()

    // ---- Kids mode ----
    var kidsMode: Boolean
        get() = sp.getBoolean("kidsMode", false)
        set(value) = sp.edit().putBoolean("kidsMode", value).apply()
    fun kids(kind: String, subId: String): Set<String>? = sp.getStringSet("kids${kind}_$subId", null)?.toSet()
    fun setKids(kind: String, subId: String, ids: Set<String>) = sp.edit().putStringSet("kids${kind}_$subId", ids).apply()
    /** Minutes after midnight, or -1 for no bedtime. */
    var kidsBedtime: Int
        get() = sp.getInt("kidsBedtime", -1)
        set(value) = sp.edit().putInt("kidsBedtime", value).apply()
    /** Minutes a day, or 0 for no limit. */
    var kidsLimit: Int
        get() = sp.getInt("kidsLimit", 0)
        set(value) = sp.edit().putInt("kidsLimit", value).apply()
    var kidsSimpleHome: Boolean
        get() = sp.getBoolean("kidsSimpleHome", true)
        set(value) = sp.edit().putBoolean("kidsSimpleHome", value).apply()
    /** "yyyymmdd:milliseconds watched" for the daily limit. */
    var kidsUsed: String
        get() = sp.getString("kidsUsed", "") ?: ""
        set(value) = sp.edit().putString("kidsUsed", value).apply()

    var pin: String?
        get() = sp.getString("pin", null)
        set(value) = sp.edit().putString("pin", value).apply()

    fun locked(subId: String): Set<String> = sp.getStringSet("locked_$subId", emptySet())?.toSet() ?: emptySet()
    fun setLocked(subId: String, ids: Set<String>) = sp.edit().putStringSet("locked_$subId", ids).apply()

    var lockAdult: Boolean
        get() = sp.getBoolean("lockAdult", false)
        set(value) = sp.edit().putBoolean("lockAdult", value).apply()

    /** "off", "menus" or "always". */
    var clock: String
        get() = sp.getString("clock", "off") ?: "off"
        set(value) = sp.edit().putString("clock", value).apply()

    var downloadFolder: String?
        get() = sp.getString("downloadFolder", null)
        set(value) = sp.edit().putString("downloadFolder", value).apply()

    var downloadWifiOnly: Boolean
        get() = sp.getBoolean("downloadWifiOnly", true)
        set(value) = sp.edit().putBoolean("downloadWifiOnly", value).apply()

    var recording: Boolean
        get() = sp.getBoolean("recording", false)
        set(value) = sp.edit().putBoolean("recording", value).apply()

    var recordingDir: String?
        get() = sp.getString("recordingDir", null)
        set(value) = sp.edit().putString("recordingDir", value).apply()

    var subtitles: Boolean
        get() = sp.getBoolean("subtitles", false)
        set(value) = sp.edit().putBoolean("subtitles", value).apply()

    var subtitleLanguage: String?
        get() = sp.getString("subtitleLanguage", null)
        set(value) = sp.edit().putString("subtitleLanguage", value).apply()

    fun categoryOrder(subId: String): List<String> =
        sp.getString("catOrder_$subId", "").orEmpty().split('\u0001').filter { it.isNotEmpty() }
    fun setCategoryOrder(subId: String, ids: List<String>) =
        sp.edit().putString("catOrder_$subId", ids.joinToString("\u0001")).apply()

    var useBackups: Boolean
        get() = sp.getBoolean("useBackups", true)
        set(value) = sp.edit().putBoolean("useBackups", value).apply()

    var preview: Boolean
        get() = sp.getBoolean("preview", true)
        set(value) = sp.edit().putBoolean("preview", value).apply()

    var stay50Hz: Boolean
        get() = sp.getBoolean("stay50Hz", false)
        set(value) = sp.edit().putBoolean("stay50Hz", value).apply()

    var animations: Boolean
        // Off for new installs; anyone who already had ShedTV keeps what they had.
        get() = if (sp.contains("animations")) sp.getBoolean("animations", false) else sp.contains("subscriptions")
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
