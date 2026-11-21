package com.flabuis.tgmusic.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Accent = Color(0xFF8B5CF6)

private val Colors = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    secondary = Color(0xFF22D3EE),
    background = Color(0xFF0E0E12),
    surface = Color(0xFF0E0E12),
    surfaceVariant = Color(0xFF1C1C24),
    surfaceContainer = Color(0xFF17171E),
    surfaceContainerHigh = Color(0xFF1F1F28),
    onBackground = Color(0xFFECECF1),
    onSurface = Color(0xFFECECF1),
    onSurfaceVariant = Color(0xFF9A9AAB),
)

@Composable
fun TgMusicTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
