package com.flabuis.tgmusic.anime

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Where you stopped: anime, episode and position, for "Продолжить просмотр". */
data class WatchEntry(
    val title: String,
    val query: String,
    val thumbFileId: Int?,
    val episode: Int,
    val total: Int?,
    val positionMs: Long,
    val durationMs: Long,
    val updated: Long,
    val watched: Set<Int>,
)

object AnimeHistory {
    private lateinit var ctx: Context
    private val _entries = MutableStateFlow<List<WatchEntry>>(emptyList())
    val entries: StateFlow<List<WatchEntry>> = _entries

    fun init(context: Context) {
        ctx = context.applicationContext
        val raw = prefs().getString("history", null) ?: return
        _entries.value = try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val w = o.optJSONArray("watched")
                WatchEntry(
                    title = o.getString("title"),
                    query = o.optString("query", o.getString("title")),
                    thumbFileId = o.optInt("thumb", 0).takeIf { it != 0 },
                    episode = o.optInt("episode", 1),
                    total = o.optInt("total", 0).takeIf { it > 0 },
                    positionMs = o.optLong("pos"),
                    durationMs = o.optLong("dur"),
                    updated = o.optLong("updated"),
                    watched = if (w == null) emptySet() else (0 until w.length()).map { w.getInt(it) }.toSet(),
                )
            }.sortedByDescending { it.updated }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun get(title: String): WatchEntry? = _entries.value.firstOrNull { it.title == title }

    fun save(entry: WatchEntry) {
        val old = get(entry.title)
        val merged = entry.copy(watched = (old?.watched.orEmpty()) + entry.watched)
        _entries.value = (listOf(merged) + _entries.value.filter { it.title != entry.title }).take(30)
        persist()
    }

    fun remove(title: String) {
        _entries.value = _entries.value.filter { it.title != title }
        persist()
    }

    private fun persist() {
        val arr = JSONArray()
        _entries.value.forEach { e ->
            arr.put(
                JSONObject()
                    .put("title", e.title).put("query", e.query).put("thumb", e.thumbFileId ?: 0)
                    .put("episode", e.episode).put("total", e.total ?: 0)
                    .put("pos", e.positionMs).put("dur", e.durationMs).put("updated", e.updated)
                    .put("watched", JSONArray(e.watched.toList()))
            )
        }
        prefs().edit().putString("history", arr.toString()).apply()
    }

    private fun prefs() = ctx.getSharedPreferences("anime", Context.MODE_PRIVATE)
}
