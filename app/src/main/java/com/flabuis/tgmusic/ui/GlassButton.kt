package com.flabuis.tgmusic.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.flabuis.tgmusic.player.BassMeter

/** Bass level smoothed per frame: jumps up on a kick, falls back softly. 0..1 */
@Composable
fun rememberBeat(playing: Boolean): State<Float> {
    val beat = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(playing) {
        while (true) {
            withFrameNanos {
                val target = if (playing) BassMeter.bass else 0f
                val v = beat.floatValue
                beat.floatValue = v + (target - v) * (if (target > v) 0.45f else 0.07f)
            }
        }
    }
    return beat
}

/**
 * See-through "glass" button. Lights up and glows in the cover color on bass hits,
 * squishes its corners when pressed.
 */
@Composable
fun GlassButton(
    onClick: () -> Unit,
    beat: Float,
    glow: Color,
    modifier: Modifier = Modifier,
    dark: Boolean = true,
    strong: Boolean = false,
    start: Dp = 28.dp,
    end: Dp = 28.dp,
    pressedCorner: Dp = 14.dp,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val springSpec = spring<Dp>(dampingRatio = 0.45f, stiffness = 500f)
    val s by animateDpAsState(if (pressed) pressedCorner else start, springSpec, label = "glassStart")
    val e by animateDpAsState(if (pressed) pressedCorner else end, springSpec, label = "glassEnd")
    val shape = RoundedCornerShape(topStart = s, bottomStart = s, topEnd = e, bottomEnd = e)

    val ink = if (dark) Color.White else Color.Black
    val fill = (if (strong) 0.22f else 0.10f) + beat * (if (strong) 0.18f else 0.12f)
    val edge = 0.18f + beat * 0.45f

    Box(
        modifier
            // glow spilling out around the button, in the cover color
            .drawBehind {
                val r = size.maxDimension * (0.75f + beat * 0.25f)
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(glow.copy(alpha = 0.55f * beat), Color.Transparent),
                        center = Offset(size.width / 2, size.height / 2),
                        radius = r,
                    ),
                    radius = r,
                )
            }
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(ink.copy(alpha = fill + 0.06f), ink.copy(alpha = fill * 0.6f))
                )
            )
            .border(
                1.dp,
                Brush.verticalGradient(listOf(ink.copy(alpha = edge), ink.copy(alpha = edge * 0.3f))),
                shape,
            )
            .clickable(interactionSource = interaction, indication = ripple(color = ink), onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
