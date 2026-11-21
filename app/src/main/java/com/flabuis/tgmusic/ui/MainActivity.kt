package com.flabuis.tgmusic.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.flabuis.tgmusic.player.PlayerConnection
import com.flabuis.tgmusic.td.ApiConfig
import com.flabuis.tgmusic.td.Td
import org.drinkless.tdlib.TdApi

class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        PlayerConnection.connect(this)
        setContent {
            // Status bar icons dark on the light theme, light otherwise.
            val dark = isAppInDarkTheme()
            DisposableEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                            else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            TgMusicTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Box(Modifier.fillMaxSize().systemBarsPadding()) {
                        Root()
                    }
                }
            }
        }
    }
}

@Composable
private fun Root() {
    val apiMissing by ApiConfig.missing.collectAsState()
    val auth by Td.authState.collectAsState()

    if (apiMissing) {
        ApiKeysScreen()
        return
    }
    when (val state = auth) {
        null, is TdApi.AuthorizationStateWaitTdlibParameters -> LoadingScreen()
        is TdApi.AuthorizationStateWaitPhoneNumber -> PhoneScreen()
        is TdApi.AuthorizationStateWaitCode -> CodeScreen(state)
        is TdApi.AuthorizationStateWaitPassword -> PasswordScreen(state)
        is TdApi.AuthorizationStateWaitEmailAddress -> EmailScreen()
        is TdApi.AuthorizationStateWaitEmailCode -> EmailCodeScreen()
        is TdApi.AuthorizationStateWaitRegistration ->
            MessageScreen("На этот номер нет аккаунта Telegram. Сначала зарегистрируйся в обычном клиенте.")
        is TdApi.AuthorizationStateWaitOtherDeviceConfirmation ->
            MessageScreen("Подтверди вход на другом устройстве")
        is TdApi.AuthorizationStateReady -> MainApp()
        is TdApi.AuthorizationStateLoggingOut -> LoadingScreen("Выходим…")
        else -> LoadingScreen()
    }
}
