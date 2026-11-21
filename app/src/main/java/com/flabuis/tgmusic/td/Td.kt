package com.flabuis.tgmusic.td

import android.content.Context
import android.os.Build
import android.util.Log
import com.flabuis.tgmusic.BuildConfig
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class TdException(val error: TdApi.Error) : IOException("${error.code}: ${error.message}")

/** Telegram API keys: from the build (GitHub Secrets) or entered by the user in the app. */
object ApiConfig {
    private const val PREFS = "api"
    private lateinit var ctx: Context

    val missing = MutableStateFlow(false)

    fun init(context: Context) {
        ctx = context.applicationContext
        // Remember keys from a build that had them, so later builds without keys
        // (public releases) keep working on this phone.
        if (BuildConfig.TG_API_ID != 0 && BuildConfig.TG_API_HASH.isNotEmpty()) {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putInt("id", BuildConfig.TG_API_ID).putString("hash", BuildConfig.TG_API_HASH).apply()
        }
        missing.value = apiId() == 0 || apiHash().isEmpty()
    }

    fun apiId(): Int =
        if (BuildConfig.TG_API_ID != 0) BuildConfig.TG_API_ID
        else ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("id", 0)

    fun apiHash(): String =
        BuildConfig.TG_API_HASH.ifEmpty {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("hash", "") ?: ""
        }

    fun save(id: Int, hash: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt("id", id).putString("hash", hash.trim()).apply()
        missing.value = false
        Td.onApiConfigReady()
    }
}

/** Thin Kotlin wrapper around the TDLib Java client. */
object Td {
    private const val TAG = "Td"

    private lateinit var appContext: Context
    @Volatile private var client: Client? = null
    @Volatile private var waitingForParams = false

    private val _authState = MutableStateFlow<TdApi.AuthorizationState?>(null)
    val authState: StateFlow<TdApi.AuthorizationState?> = _authState

    private val _fileUpdates = MutableSharedFlow<TdApi.File>(extraBufferCapacity = 512)
    val fileUpdates: SharedFlow<TdApi.File> = _fileUpdates

    fun init(context: Context) {
        appContext = context.applicationContext
        try {
            Client.execute(TdApi.SetLogVerbosityLevel(1))
        } catch (e: Throwable) {
            Log.w(TAG, "Can't set log level", e)
        }
        createClient()
    }

    private fun createClient() {
        client = Client.create({ onUpdate(it) }, { Log.e(TAG, "update error", it) }, { Log.e(TAG, "error", it) })
    }

    private fun onUpdate(obj: TdApi.Object) {
        when (obj) {
            is TdApi.UpdateAuthorizationState -> {
                val state = obj.authorizationState
                _authState.value = state
                when (state) {
                    is TdApi.AuthorizationStateWaitTdlibParameters -> sendParameters()
                    is TdApi.AuthorizationStateClosed -> createClient() // after log out: start fresh
                    else -> {}
                }
            }
            is TdApi.UpdateFile -> _fileUpdates.tryEmit(obj.file)
        }
    }

    fun onApiConfigReady() {
        if (waitingForParams) sendParameters()
    }

    private fun sendParameters() {
        if (ApiConfig.apiId() == 0 || ApiConfig.apiHash().isEmpty()) {
            waitingForParams = true
            return
        }
        waitingForParams = false
        val params = TdApi.SetTdlibParameters().apply {
            useTestDc = false
            databaseDirectory = File(appContext.filesDir, "tdlib").absolutePath
            filesDirectory = File(appContext.filesDir, "tdfiles").absolutePath
            databaseEncryptionKey = ByteArray(0)
            useFileDatabase = true
            useChatInfoDatabase = true
            useMessageDatabase = true
            useSecretChats = false
            apiId = ApiConfig.apiId()
            apiHash = ApiConfig.apiHash()
            systemLanguageCode = "ru"
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"
            systemVersion = "Android ${Build.VERSION.RELEASE}"
            applicationVersion = "TgMusic ${BuildConfig.VERSION_NAME}"
        }
        client?.send(params) { result ->
            if (result is TdApi.Error) Log.e(TAG, "setTdlibParameters: ${result.message}")
        }
    }

    /** Sends a request and suspends until the result arrives. Throws [TdException] on error. */
    @Suppress("UNCHECKED_CAST")
    suspend fun <T : TdApi.Object> send(function: TdApi.Function<T>): T =
        suspendCancellableCoroutine { cont ->
            val c = client
            if (c == null) {
                cont.resumeWithException(IOException("TDLib not ready"))
                return@suspendCancellableCoroutine
            }
            c.send(function) { result ->
                if (result is TdApi.Error) cont.resumeWithException(TdException(result))
                else cont.resume(result as T)
            }
        }

    /** Blocking version for background threads (player). Never call from the TDLib thread. */
    @Suppress("UNCHECKED_CAST")
    fun <T : TdApi.Object> sendBlocking(function: TdApi.Function<T>, timeoutMs: Long = 30_000): T {
        val c = client ?: throw IOException("TDLib not ready")
        val latch = CountDownLatch(1)
        var result: TdApi.Object? = null
        c.send(function) {
            result = it
            latch.countDown()
        }
        if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) throw IOException("TDLib timeout")
        val r = result
        if (r is TdApi.Error) throw TdException(r)
        return r as T
    }

    /** Fire-and-forget request. */
    fun sendAsync(function: TdApi.Function<*>) {
        client?.send(function, null)
    }
}
