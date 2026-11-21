package com.flabuis.tgmusic.data

import android.content.Context
import com.flabuis.tgmusic.td.Td
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi

enum class AppTheme(val title: String) {
    SYSTEM("Как в системе"),
    LIGHT("Светлая"),
    DARK("Тёмная"),
    AMOLED("Очень тёмная (AMOLED)"),
}

object Settings {
    private lateinit var ctx: Context
    private val _theme = MutableStateFlow(AppTheme.SYSTEM)
    val theme: StateFlow<AppTheme> = _theme

    private val _liveBackground = MutableStateFlow(true)
    /** Animated background that reacts to the bass. */
    val liveBackground: StateFlow<Boolean> = _liveBackground

    fun setLiveBackground(on: Boolean) {
        _liveBackground.value = on
        prefs().edit().putBoolean("live_bg", on).apply()
    }

    private val _crossfadeSec = MutableStateFlow(6)
    /** Seconds the end of one track overlaps the start of the next; 0 = off. */
    val crossfadeSec: StateFlow<Int> = _crossfadeSec

    fun setCrossfade(sec: Int) {
        _crossfadeSec.value = sec
        prefs().edit().putInt("crossfade", sec).apply()
    }

    private val _liveBrightness = MutableStateFlow(1f)
    /** Brightness of the live background in the app (0.3..1.8, 1 = default). */
    val liveBrightness: StateFlow<Float> = _liveBrightness
    private val _idleBrightness = MutableStateFlow(1f)
    /** Same for the idle screen. */
    val idleBrightness: StateFlow<Float> = _idleBrightness
    private val _lyricsColor = MutableStateFlow(0xFFFFFFFF.toInt())
    /** Color of lyrics text (ARGB). 0 = take it from the album cover. */
    val lyricsColor: StateFlow<Int> = _lyricsColor

    fun setLiveBrightness(v: Float) { _liveBrightness.value = v; prefs().edit().putFloat("live_brightness", v).apply() }
    fun setIdleBrightness(v: Float) { _idleBrightness.value = v; prefs().edit().putFloat("idle_brightness", v).apply() }
    fun setLyricsColor(argb: Int) { _lyricsColor.value = argb; prefs().edit().putInt("lyrics_color", argb).apply() }

    private val _bassVibration = MutableStateFlow(false)
    /** Phone vibrates on bass hits, stronger bass = stronger buzz. Off by default. */
    val bassVibration: StateFlow<Boolean> = _bassVibration
    fun setBassVibration(on: Boolean) { _bassVibration.value = on; prefs().edit().putBoolean("bass_vibration", on).apply() }

    fun init(context: Context) {
        ctx = context.applicationContext
        _bassVibration.value = prefs().getBoolean("bass_vibration", false)
        _liveBrightness.value = prefs().getFloat("live_brightness", 1f)
        _idleBrightness.value = prefs().getFloat("idle_brightness", 1f)
        _lyricsColor.value = prefs().getInt("lyrics_color", 0xFFFFFFFF.toInt())
        _crossfadeSec.value = prefs().getInt("crossfade", 6)
        _liveBackground.value = prefs().getBoolean("live_bg", true)
        val saved = prefs().getString("theme", null)
        _theme.value = AppTheme.entries.firstOrNull { it.name == saved } ?: AppTheme.SYSTEM
    }

    fun setTheme(theme: AppTheme) {
        _theme.value = theme
        prefs().edit().putString("theme", theme.name).apply()
    }

    private fun prefs() = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
}

/** Which tracks are fully saved on the phone (played once = kept for offline). */
object Downloads {
    private val _done = MutableStateFlow<Set<Int>>(emptySet())
    val done: StateFlow<Set<Int>> = _done

    fun observe(scope: CoroutineScope) {
        scope.launch {
            Td.fileUpdates.collect { f ->
                if (f.local.isDownloadingCompleted) {
                    if (f.id !in _done.value) _done.value = _done.value + f.id
                } else if (f.id in _done.value) {
                    _done.value = _done.value - f.id
                }
            }
        }
    }

    suspend fun check(fileId: Int) {
        val f = try { Td.send(TdApi.GetFile(fileId)) } catch (e: Exception) { return }
        if (f.local.isDownloadingCompleted && fileId !in _done.value) _done.value = _done.value + fileId
    }

    /** Size of downloaded music and covers, in bytes. */
    suspend fun cacheSize(): Long = Td.send(TdApi.GetStorageStatisticsFast()).filesSize

    suspend fun clear() {
        Td.send(
            TdApi.OptimizeStorage(
                0, 0, 0, 0,
                arrayOf(TdApi.FileTypeAudio(), TdApi.FileTypeDocument(), TdApi.FileTypeThumbnail()),
                LongArray(0), LongArray(0), false, 20
            )
        )
        _done.value = emptySet()
    }
}
