package com.matchday.tv.data

import android.content.Context

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("matchday", Context.MODE_PRIVATE)

    var credentials: Credentials?
        get() {
            val server = sp.getString("server", null) ?: return null
            val user = sp.getString("username", null) ?: return null
            val pass = sp.getString("password", null) ?: return null
            return Credentials(server, user, pass)
        }
        set(value) {
            sp.edit().apply {
                if (value == null) {
                    remove("server"); remove("username"); remove("password")
                } else {
                    putString("server", value.server)
                    putString("username", value.username)
                    putString("password", value.password)
                }
            }.apply()
        }

    var favourites: Set<String>
        get() = sp.getStringSet("favourites", emptySet())?.toSet() ?: emptySet()
        set(value) = sp.edit().putStringSet("favourites", value).apply()

    /** "ts" (MPEG-TS) or "m3u8" (HLS). */
    var streamFormat: String
        get() = sp.getString("streamFormat", "ts") ?: "ts"
        set(value) = sp.edit().putString("streamFormat", value).apply()
}
