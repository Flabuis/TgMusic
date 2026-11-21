package com.flabuis.tgmusic.data

import android.content.Context
import com.flabuis.tgmusic.BuildConfig
import com.flabuis.tgmusic.player.PlayerConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.abs

data class LyricLine(val timeMs: Long, val text: String)

sealed interface LyricsState {
    data object None : LyricsState
    data object Loading : LyricsState
    data object NotFound : LyricsState
    /** [synced] is null when only plain text exists. */
    data class Ready(val synced: List<LyricLine>?, val plain: String?) : LyricsState
}

/**
 * Lyrics for the current track from LRCLIB (free, open, no key).
 * Results are cached on disk, so a song is looked up only once.
 */
object Lyrics {
    private lateinit var cacheDir: File
    private val _state = MutableStateFlow<LyricsState>(LyricsState.None)
    val state: StateFlow<LyricsState> = _state

    private const val NOT_FOUND_RETRY_MS = 3L * 24 * 3600 * 1000

    fun start(context: Context, scope: CoroutineScope) {
        cacheDir = File(context.filesDir, "lyrics").apply { mkdirs() }
        scope.launch {
            PlayerConnection.state
                .map { Triple(it.mediaId, it.title, it.artist) }
                .distinctUntilChanged { a, b -> a.first == b.first }
                .collectLatest { (id, title, artist) ->
                    if (id == null || title.isBlank()) {
                        _state.value = LyricsState.None
                        return@collectLatest
                    }
                    _state.value = LyricsState.Loading
                    _state.value = try {
                        withContext(Dispatchers.IO) { load(title, artist, PlayerConnection.state.value.durationMs) }
                    } catch (e: Exception) {
                        LyricsState.NotFound
                    }
                }
        }
    }

    private fun load(rawTitle: String, rawArtist: String, durationMs: Long): LyricsState {
        val title = clean(rawTitle)
        val artist = rawArtist.takeUnless { it == "Неизвестен" }?.let { clean(it) }.orEmpty()
        val file = File(cacheDir, "${(artist + "|" + title).lowercase().hashCode()}.json")

        if (file.exists()) {
            val cached = JSONObject(file.readText())
            if (!cached.optBoolean("none")) return fromJson(cached)
            if (System.currentTimeMillis() - cached.optLong("t") < NOT_FOUND_RETRY_MS) return LyricsState.NotFound
        }

        val results = buildList {
            if (artist.isNotEmpty()) addAll(search("track_name=${enc(title)}&artist_name=${enc(artist)}"))
            if (isEmpty()) addAll(search("q=${enc("$artist $title".trim())}"))
        }.filter { !it.optBoolean("instrumental") }

        val durationSec = durationMs / 1000.0
        val best = results
            .sortedWith(
                compareBy<JSONObject>(
                    { if (it.isNull("syncedLyrics")) 1 else 0 },
                    { if (durationSec > 0) abs(it.optDouble("duration", 0.0) - durationSec) else 0.0 },
                )
            )
            .firstOrNull { !it.isNull("syncedLyrics") || !it.isNull("plainLyrics") }

        if (best == null) {
            file.writeText(JSONObject().put("none", true).put("t", System.currentTimeMillis()).toString())
            return LyricsState.NotFound
        }
        val saved = JSONObject()
            .put("synced", best.optString("syncedLyrics").takeIf { !best.isNull("syncedLyrics") })
            .put("plain", best.optString("plainLyrics").takeIf { !best.isNull("plainLyrics") })
        file.writeText(saved.toString())
        return fromJson(saved)
    }

    private fun fromJson(o: JSONObject): LyricsState {
        val synced = o.optString("synced").takeIf { it.isNotBlank() && !o.isNull("synced") }?.let { parseLrc(it) }
        val plain = o.optString("plain").takeIf { it.isNotBlank() && !o.isNull("plain") }
        return if (synced.isNullOrEmpty() && plain == null) LyricsState.NotFound
               else LyricsState.Ready(synced?.takeIf { it.isNotEmpty() }, plain)
    }

    private val timeTag = Regex("""\[(\d+):(\d+)(?:[.:](\d+))?]""")

    /** "[01:23.45] text" -> LyricLine(83450, "text"). Lines can have several time tags. */
    fun parseLrc(lrc: String): List<LyricLine> {
        val out = mutableListOf<LyricLine>()
        for (line in lrc.lines()) {
            val tags = timeTag.findAll(line).toList()
            if (tags.isEmpty()) continue
            val text = line.substring(tags.last().range.last + 1).trim()
            for (t in tags) {
                val min = t.groupValues[1].toLong()
                val sec = t.groupValues[2].toLong()
                val frac = t.groupValues[3]
                val ms = when (frac.length) {
                    0 -> 0L
                    1 -> frac.toLong() * 100
                    2 -> frac.toLong() * 10
                    else -> frac.take(3).toLong()
                }
                out += LyricLine(min * 60_000 + sec * 1000 + ms, text)
            }
        }
        return out.sortedBy { it.timeMs }
    }

    private fun search(query: String): List<JSONObject> {
        val conn = URL("https://lrclib.net/api/search?$query").openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        conn.setRequestProperty("User-Agent", "TgMusic/${BuildConfig.VERSION_NAME} (https://github.com/Flabuis/TgMusic)")
        return try {
            if (conn.responseCode != 200) return emptyList()
            val arr = JSONArray(conn.inputStream.bufferedReader().readText())
            (0 until arr.length()).map { arr.getJSONObject(it) }
        } finally {
            conn.disconnect()
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    /** Drops junk like "(Official Video)", "[Bass Boosted]", "feat. X", "| text". */
    private fun clean(s: String): String = s
        .replace(Regex("""[(\[][^)\]]*(official|video|audio|lyrics|bass|boost|slowed|speed|remaster|prod|clip|клип|текст)[^)\]]*[)\]]""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\s+(feat\.?|ft\.?)\s+.*$""", RegexOption.IGNORE_CASE), "")
        .substringBefore(" | ")
        .replace('_', ' ')
        .trim()
}
