package com.flabuis.tgmusic.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.core.content.FileProvider
import com.flabuis.tgmusic.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val version: String, val code: Int, val notes: String, val url: String, val size: Long) : UpdateState
    data class Downloading(val version: String, val progress: Float) : UpdateState
    data class Ready(val version: String, val file: File) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/** Checks GitHub releases of TgMusic and installs a newer APK. */
object Updater {
    private const val LATEST = "https://api.github.com/repos/Flabuis/TgMusic/releases/latest"
    private const val CHECK_EVERY_MS = 3L * 3600 * 1000

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state

    private var lastCheck = 0L

    /** Quiet check on app start; shows nothing if offline or nothing new. */
    suspend fun checkInBackground() {
        if (System.currentTimeMillis() - lastCheck < CHECK_EVERY_MS) return
        if (_state.value !is UpdateState.Idle && _state.value !is UpdateState.UpToDate) return
        val result = check()
        if (result is UpdateState.Failed) _state.value = UpdateState.Idle
    }

    suspend fun check(): UpdateState {
        _state.value = UpdateState.Checking
        lastCheck = System.currentTimeMillis()
        val result = try {
            withContext(Dispatchers.IO) { fetchLatest() }
        } catch (e: Exception) {
            UpdateState.Failed("Не удалось проверить: ${e.message}")
        }
        _state.value = result
        return result
    }

    private fun fetchLatest(): UpdateState {
        val conn = URL(LATEST).openConnection() as HttpURLConnection
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("User-Agent", "TgMusic/${BuildConfig.VERSION_NAME}")
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        try {
            when (conn.responseCode) {
                200 -> {}
                404 -> return UpdateState.Failed("Репозиторий закрыт или релизов нет")
                else -> return UpdateState.Failed("GitHub ответил ${conn.responseCode}")
            }
            val json = JSONObject(conn.inputStream.bufferedReader().readText())
            val tag = json.optString("tag_name") // "v1.0.27"
            val code = tag.substringAfterLast('.').toIntOrNull() ?: return UpdateState.UpToDate
            if (code <= BuildConfig.VERSION_CODE) return UpdateState.UpToDate
            val assets = json.optJSONArray("assets") ?: return UpdateState.UpToDate
            val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
                .firstOrNull { it.optString("name").endsWith(".apk") } ?: return UpdateState.UpToDate
            return UpdateState.Available(
                version = tag.removePrefix("v"),
                code = code,
                notes = json.optString("body").trim(),
                url = apk.optString("browser_download_url"),
                size = apk.optLong("size"),
            )
        } finally {
            conn.disconnect()
        }
    }

    suspend fun download(context: Context, update: UpdateState.Available) {
        lastOffer = update
        _state.value = UpdateState.Downloading(update.version, 0f)
        try {
            val file = withContext(Dispatchers.IO) {
                val dir = File(context.cacheDir, "updates").apply { mkdirs() }
                dir.listFiles()?.forEach { it.delete() }
                val out = File(dir, "TgMusic-${update.version}.apk")
                var url = URL(update.url)
                var conn: HttpURLConnection
                // GitHub redirects to its file storage; follow it by hand to be safe.
                var hops = 0
                while (true) {
                    conn = url.openConnection() as HttpURLConnection
                    conn.instanceFollowRedirects = false
                    conn.setRequestProperty("User-Agent", "TgMusic/${BuildConfig.VERSION_NAME}")
                    conn.connectTimeout = 15_000
                    conn.readTimeout = 30_000
                    val code = conn.responseCode
                    if (code in 300..399 && hops++ < 5) {
                        url = URL(url, conn.getHeaderField("Location"))
                        conn.disconnect()
                        continue
                    }
                    if (code != 200) throw IllegalStateException("Ошибка загрузки: $code")
                    break
                }
                val total = conn.contentLengthLong.takeIf { it > 0 } ?: update.size
                conn.inputStream.use { input ->
                    out.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        var lastEmit = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            output.write(buf, 0, n)
                            done += n
                            if (done - lastEmit > 256 * 1024 && total > 0) {
                                lastEmit = done
                                _state.value = UpdateState.Downloading(update.version, done.toFloat() / total)
                            }
                        }
                    }
                }
                conn.disconnect()
                out
            }
            // The screen opens the installer itself: launching it from here (app context, after a
            // background download) gets silently blocked on MIUI/HyperOS and some other phones.
            _state.value = UpdateState.Ready(update.version, file)
        } catch (e: Exception) {
            _state.value = UpdateState.Failed(e.message ?: "Ошибка загрузки")
        }
    }

    /** True if Android lets TgMusic install APKs (the "unknown sources" switch for this app). */
    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()

    /** Settings page with the "allow installing from TgMusic" switch. */
    fun permissionIntent(context: Context): Intent =
        Intent(AndroidSettings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    /** Opens Android's installer. Call with an Activity context. */
    fun install(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            )
        } catch (e: Exception) {
            _state.value = UpdateState.Failed("Не открылся установщик: ${e.message}")
        }
    }

    /** Back to "available" so the card can retry. */
    fun retry(update: UpdateState.Available) {
        _state.value = update
    }

    private var lastOffer: UpdateState.Available? = null
    fun lastAvailable(): UpdateState.Available? = lastOffer

    fun dismiss() {
        _state.value = UpdateState.Idle
    }
}
