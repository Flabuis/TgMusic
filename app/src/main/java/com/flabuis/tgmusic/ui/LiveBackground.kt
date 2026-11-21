package com.flabuis.tgmusic.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.lerp
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import com.flabuis.tgmusic.player.BassMeter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** A few colors from the album cover for the live background. */
@Composable
fun rememberCoverPalette(fileId: Int?, fallback: List<Color>): List<Color> {
    var colors by remember(fileId) { mutableStateOf<List<Color>?>(null) }
    LaunchedEffect(fileId) {
        val id = fileId ?: return@LaunchedEffect
        val bmp = try { ImageCache.load(id) } catch (e: Exception) { null } ?: return@LaunchedEffect
        colors = withContext(Dispatchers.Default) {
            val p = Palette.from(bmp.asAndroidBitmap()).maximumColorCount(16).generate()
            listOfNotNull(p.vibrantSwatch, p.darkVibrantSwatch, p.mutedSwatch, p.lightVibrantSwatch, p.dominantSwatch)
                .map { it.rgb }.distinct().take(4)
                .map { Color(ColorUtils.blendARGB(it, android.graphics.Color.BLACK, 0.15f)) }
                .takeIf { it.size >= 2 }
        }
    }
    return colors ?: fallback
}

/**
 * Living background: soft color blobs drifting around.
 * They swell on bass hits, and move faster and warmer on energetic songs.
 * Everything keeps moving, so nothing burns into an AMOLED screen.
 */
@Composable
fun LiveBackground(
    colors: List<Color>,
    modifier: Modifier = Modifier,
    base: Color = Color.Black,
    intensity: Float = 1f,
    playing: Boolean = true,
) {
    var time by remember { mutableFloatStateOf(0f) }
    var bass by remember { mutableFloatStateOf(0f) }
    var energy by remember { mutableFloatStateOf(0f) }
    var mood by remember { mutableFloatStateOf(0.4f) }

    LaunchedEffect(playing) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1e9f).coerceAtMost(0.05f)
                last = now
                val targetBass = if (playing) BassMeter.bass else 0f
                val targetEnergy = if (playing) BassMeter.energy else 0f
                // fast attack, slower release: looks like it breathes with the kick
                bass += (targetBass - bass) * (if (targetBass > bass) 0.45f else 0.08f)
                energy += (targetEnergy - energy) * 0.1f
                mood += (BassMeter.mood - mood) * 0.02f
                // calm songs drift slowly, energetic ones swirl
                time += dt * (0.12f + mood * 0.5f + energy * 0.15f)
            }
        }
    }

    val palette = if (colors.isEmpty()) listOf(Color(0xFF4C2C8F), Color(0xFF1E3A8A)) else colors
    Canvas(modifier.fillMaxSize()) {
        drawRect(base)
        val w = size.width
        val h = size.height
        val minSide = minOf(w, h)
        val glow = (0.35f + bass * 0.45f + energy * 0.2f) * intensity
        palette.forEachIndexed { i, c ->
            val phase = i * 1.9f
            val center = Offset(
                w * (0.5f + 0.38f * sin(time * (0.7f + i * 0.23f) + phase)),
                h * (0.5f + 0.36f * cos(time * (0.5f + i * 0.31f) + phase * 1.3f)),
            )
            // warmer tint on energetic songs
            val tinted = lerp(c, Color(0xFFFF5A36), (mood - 0.5f).coerceIn(0f, 0.35f))
            val radius = minSide * (0.55f + 0.08f * i) * (1f + bass * 0.35f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(tinted.copy(alpha = max(0f, glow).coerceAtMost(0.9f)), Color.Transparent),
                    center = center,
                    radius = radius,
                ),
                radius = radius,
                center = center,
            )
        }
    }
}
