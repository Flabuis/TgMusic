package com.flabuis.tgmusic.ui

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/** Bass level for UI, 0..1 (shared with the live background). */
@Composable
fun rememberBeat(@Suppress("UNUSED_PARAMETER") playing: Boolean): State<Float> =
    remember { derivedStateOf { LiveClock.bass } }

/** Where the screen's background sits, so glass can show the right piece of it. */
class Backdrop {
    var origin by mutableStateOf(Offset.Zero)
    var size by mutableStateOf(IntSize.Zero)

    /** Put on the full-screen background container. */
    fun Modifier.trackBackdrop(): Modifier = onGloballyPositioned {
        origin = it.positionInRoot()
        size = it.size
    }
}

/**
 * Water-drop lens: bends the picture towards the edges, magnifies the middle a bit
 * and splits colors slightly at the rim, like light through a drop.
 */
private const val LENS = """
uniform shader content;
uniform float2 size;
uniform float radius;
uniform float strength;

half4 main(float2 p) {
    float2 c = size * 0.5;
    float2 q = abs(p - c) - (c - radius);
    float d = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;   // < 0 inside
    float depth = clamp(-d / (min(size.x, size.y) * 0.45), 0.0, 1.0);    // 0 rim .. 1 middle
    float bend = pow(1.0 - depth, 2.2);
    float2 dir = normalize(p - c + float2(0.0001, 0.0001));
    float2 s = c + (p - c) * 0.9 - dir * bend * strength;                // magnify + refract
    float2 ca = dir * bend * 3.0;                                        // chromatic split
    half4 g = content.eval(s);
    half r = content.eval(s + ca).r;
    half b = content.eval(s - ca).b;
    return half4(r, g.g, b, g.a);
}
"""

/**
 * "Liquid glass" button: you see the live background through it, bent like through a drop
 * of water, with a soft highlight and a thin bright rim. It doesn't glow by itself;
 * on bass hits the drop wobbles a little. Squishes when pressed.
 */
@Composable
fun GlassButton(
    onClick: () -> Unit,
    beat: Float,
    backdrop: Backdrop,
    background: @Composable () -> Unit,
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
    var position by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    val shader = remember { if (Build.VERSION.SDK_INT >= 33) RuntimeShader(LENS) else null }
    val ink = if (dark) Color.White else Color.Black

    Box(
        modifier
            .onGloballyPositioned { position = it.positionInRoot() }
            // a drop "wobbles" a touch on kicks
            .graphicsLayer {
                val wobble = 1f + beat * 0.025f
                scaleX = wobble
                scaleY = 2f - wobble
            }
            .clip(shape),
        contentAlignment = Alignment.Center,
    ) {
        // 1. The background behind the button, lined up with the real one, seen through the lens.
        if (shader != null && backdrop.size != IntSize.Zero) {
            val offset = position - backdrop.origin
            Box(
                Modifier.matchParentSize().graphicsLayer {
                    shader.setFloatUniform("size", size.width, size.height)
                    shader.setFloatUniform("radius", with(density) { minOf(s, e).toPx() })
                    shader.setFloatUniform("strength", with(density) { (14.dp + 6.dp * beat).toPx() })
                    renderEffect = RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
                    clip = true
                }
            ) {
                // place the full-size background so that its pixels match the screen behind
                Layout(content = background) { measurables, constraints ->
                    val full = Constraints.fixed(backdrop.size.width, backdrop.size.height)
                    val placeables = measurables.map { it.measure(full) }
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        placeables.forEach { it.place(-offset.x.toInt(), -offset.y.toInt()) }
                    }
                }
            }
        }
        // 2. Glass body: a faint tint, a bit denser for the main button.
        Box(Modifier.matchParentSize().background(ink.copy(alpha = if (strong) 0.14f else 0.07f)))
        // 3. Light reflected off the top of the drop.
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(
                    0f to Color.White.copy(alpha = if (dark) 0.22f else 0.45f),
                    0.45f to Color.White.copy(alpha = 0f),
                    1f to Color.Black.copy(alpha = if (dark) 0.12f else 0.04f),
                )
            )
        )
        // 4. Thin rim: bright where light hits (top-left), almost gone at the bottom.
        Box(
            Modifier.matchParentSize()
                .border(
                    1.dp,
                    Brush.linearGradient(
                        listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.25f)),
                    ),
                    shape,
                )
                .clickable(interactionSource = interaction, indication = ripple(color = ink), onClick = onClick)
        )
        content()
    }
}
