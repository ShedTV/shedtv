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

/** Resume points for movies and episodes, newest first. */
class WatchHistory(context: Context) {
    private val sp = context.getSharedPreferences("matchday_history", Context.MODE_PRIVATE)

    fun all(): List<WatchEntry> {
        val raw = sp.getString(KEY, null) ?: return emptyList()
        return try {
            (Json.parseToJsonElement(raw) as JsonArray).mapNotNull { el ->
                val o = el as? JsonObject ?: return@mapNotNull null
                WatchEntry(
                    kind = o.s("kind") ?: return@mapNotNull null,
                    id = o.s("id") ?: return@mapNotNull null,
                    title = o.s("title").orEmpty(),
                    subtitle = o.s("subtitle").orEmpty(),
                    image = o.s("image"),
                    parentId = o.s("parentId"),
                    position = o.s("position")?.toLongOrNull() ?: 0,
                    duration = o.s("duration")?.toLongOrNull() ?: 0,
                    updated = o.s("updated")?.toLongOrNull() ?: 0,
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun get(kind: String, id: String): WatchEntry? = all().firstOrNull { it.kind == kind && it.id == id }

    fun save(entry: WatchEntry) {
        val list = listOf(entry) + all().filterNot { it.kind == entry.kind && it.id == entry.id }
        write(list.take(MAX))
    }

    fun remove(kind: String, id: String) = write(all().filterNot { it.kind == kind && it.id == id })

    private fun write(list: List<WatchEntry>) {
        val arr = buildJsonArray {
            list.forEach { e ->
                add(buildJsonObject {
                    put("kind", e.kind); put("id", e.id); put("title", e.title); put("subtitle", e.subtitle)
                    e.image?.let { put("image", it) }
                    e.parentId?.let { put("parentId", it) }
                    put("position", e.position.toString()); put("duration", e.duration.toString()); put("updated", e.updated.toString())
                })
            }
        }
        sp.edit().putString(KEY, arr.toString()).apply()
    }

    private fun JsonObject.s(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull

    companion object {
        private const val KEY = "entries"
        private const val MAX = 40
    }
}
