package com.flabuis.tgmusic.ui

import android.os.Build
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.flabuis.tgmusic.data.AppTheme
import com.flabuis.tgmusic.data.Settings
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

val Accent = Color(0xFF8B5CF6)

/** Fallback palette for Android < 12 (no wallpaper colors). */
private val FallbackColors = darkColorScheme(
    primary = Color(0xFFCDB8FF),
    onPrimary = Color(0xFF35106E),
    primaryContainer = Color(0xFF4C2C8F),
    onPrimaryContainer = Color(0xFFE8DDFF),
    secondary = Color(0xFFCBC2DB),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE8DEF8),
    tertiary = Color(0xFFFFB0C8),
    tertiaryContainer = Color(0xFF633B48),
    background = Color(0xFF141218),
    surface = Color(0xFF141218),
    surfaceContainerLow = Color(0xFF1D1B20),
    surfaceContainer = Color(0xFF211F26),
    surfaceContainerHigh = Color(0xFF2B2930),
    surfaceContainerHighest = Color(0xFF36343B),
    onSurface = Color(0xFFE6E0E9),
    onSurfaceVariant = Color(0xFFCAC4D0),
)

private val FallbackLight = lightColorScheme(
    primary = Color(0xFF65558F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE9DDFF),
    onPrimaryContainer = Color(0xFF22005D),
    secondaryContainer = Color(0xFFE8DEF8),
    onSecondaryContainer = Color(0xFF1D192B),
    tertiaryContainer = Color(0xFFFFD8E4),
    onTertiaryContainer = Color(0xFF31111D),
)

/** Pure black backgrounds: pixels are off on AMOLED screens. */
private fun ColorScheme.amoled() = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0A0A0C),
    surfaceContainer = Color(0xFF101013),
    surfaceContainerHigh = Color(0xFF17171B),
    surfaceContainerHighest = Color(0xFF1E1E23),
)

@Composable
fun isAppInDarkTheme(): Boolean {
    val theme by Settings.theme.collectAsState()
    return when (theme) {
        AppTheme.SYSTEM -> isSystemInDarkTheme()
        AppTheme.LIGHT -> false
        AppTheme.DARK, AppTheme.AMOLED -> true
    }
}

/** Material 3 Expressive: wallpaper colors (Material You) + expressive motion. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TgMusicTheme(content: @Composable () -> Unit) {
    val theme by Settings.theme.collectAsState()
    val dark = isAppInDarkTheme()
    val context = LocalContext.current
    val dynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val base = when {
        dark && dynamic -> dynamicDarkColorScheme(context)
        dark -> FallbackColors
        dynamic -> dynamicLightColorScheme(context)
        else -> FallbackLight
    }
    val colors = if (theme == AppTheme.AMOLED) base.amoled() else base
    MaterialExpressiveTheme(colorScheme = colors, motionScheme = MotionScheme.expressive(), content = content)
}
