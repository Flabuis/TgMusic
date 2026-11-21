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

    fun init(context: Context) {
        ctx = context.applicationContext
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
