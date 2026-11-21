@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.flabuis.tgmusic.ui

import android.app.Activity
import android.view.WindowManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.palette.graphics.Palette
import com.flabuis.tgmusic.data.LyricLine
import com.flabuis.tgmusic.data.Settings
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.layout.offset
import com.flabuis.tgmusic.data.Lyrics
import com.flabuis.tgmusic.data.LyricsState
import com.flabuis.tgmusic.player.PlayerConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Index of the line being sung right now (-1 before the first line). */
private fun activeLine(lines: List<LyricLine>, posMs: Long): Int =
    lines.indexOfLast { it.timeMs <= posMs + 250 }

/** Deep color picked from the album cover, like Spotify's lyrics background. */
@Composable
fun rememberCoverColor(fileId: Int?, fallback: Color): Color {
    var picked by remember(fileId) { mutableStateOf<Color?>(null) }
    LaunchedEffect(fileId) {
        val id = fileId ?: return@LaunchedEffect
        val bmp = try { ImageCache.load(id) } catch (e: Exception) { null } ?: return@LaunchedEffect
        picked = withContext(Dispatchers.Default) {
            val p = Palette.from(bmp.asAndroidBitmap()).generate()
            val rgb = (p.darkVibrantSwatch ?: p.vibrantSwatch ?: p.darkMutedSwatch ?: p.dominantSwatch)?.rgb
            rgb?.let { Color(ColorUtils.blendARGB(it, android.graphics.Color.BLACK, 0.35f)) }
        }
    }
    return animateColorAsState(picked ?: fallback, tween(600), label = "coverColor").value
}

// ---------- Full-screen lyrics ----------

@Composable
fun LyricsScreen(onClose: () -> Unit) {
    val s by PlayerConnection.state.collectAsState()
    val allLyrics by Lyrics.state.collectAsState()
    val lyrics = Lyrics.stateFor(allLyrics, s.mediaId)
    val pos by rememberPosition()
    val bg = rememberCoverColor(s.coverLarge ?: s.coverSmall, MaterialTheme.colorScheme.primaryContainer)

    val live by Settings.liveBackground.collectAsState()
    val palette = rememberCoverPalette(s.coverLarge ?: s.coverSmall, listOf(bg))
    if (live) LiveBackground(colors = palette, base = bg, intensity = 0.65f, playing = s.isPlaying)
    Column(Modifier.fillMaxSize().background(if (live) Color.Transparent else bg)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose, shapes = IconButtonDefaults.shapes()) {
                Icon(AppIcons.Down, "Свернуть", tint = Color.White)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(s.title, color = Color.White, style = MaterialTheme.typography.titleSmallEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(s.artist, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(48.dp))
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (val l = lyrics) {
                LyricsState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LoadingIndicator(Modifier.size(64.dp), color = Color.White)
                }
                LyricsState.NotFound, LyricsState.None -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "Текст для этого трека не нашёлся",
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        textAlign = TextAlign.Center,
                    )
                }
                is LyricsState.Ready -> {
                    val synced = l.synced
                    if (synced != null) {
                        androidx.compose.runtime.key(s.mediaId) { SyncedLyrics(synced, pos) }
                    } else {
                        PlainLyrics(l.plain.orEmpty())
                    }
                }
            }
        }

        // Thin progress at the bottom, like Spotify.
        val progress = if (s.durationMs > 0) (pos.toFloat() / s.durationMs).coerceIn(0f, 1f) else 0f
        LinearWavyProgressIndicator(
            progress = { progress },
            amplitude = { if (s.isPlaying) 1f else 0f },
            color = Color.White,
            trackColor = Color.White.copy(alpha = 0.25f),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
        )
    }
}

@Composable
private fun SyncedLyrics(lines: List<LyricLine>, pos: Long) {
    val active = activeLine(lines, pos)
    val listState = rememberLazyListState()
    val dragged by listState.interactionSource.collectIsDraggedAsState()
    var lastUserScroll by remember { mutableLongStateOf(0L) }
    LaunchedEffect(dragged) { if (dragged) lastUserScroll = System.currentTimeMillis() }

    // Follow the song, unless the user scrolled by hand in the last few seconds.
    // Glide to the line with a smooth curve instead of jumping.
    LaunchedEffect(active) {
        if (active < 0 || System.currentTimeMillis() - lastUserScroll <= 3000) return@LaunchedEffect
        val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == active }
        if (info != null) {
            listState.animateScrollBy(info.offset.toFloat(), tween(750, easing = FastOutSlowInEasing))
        } else {
            listState.animateScrollToItem(active)
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = maxHeight * 0.3f, bottom = maxHeight * 0.6f),
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(lines) { index, line ->
                val isActive = index == active
                val distance = if (active < 0) index + 1 else kotlin.math.abs(index - active)
                // Spotify look: sung line white, already sung dimmed, upcoming dark.
                val color by animateColorAsState(
                    when {
                        isActive -> Color.White
                        index < active -> Color.White.copy(alpha = 0.5f)
                        else -> Color.Black.copy(alpha = 0.55f)
                    },
                    tween(450),
                    label = "lineColor",
                )
                val scale by animateFloatAsState(
                    if (isActive) 1f else 0.93f,
                    spring(dampingRatio = 0.55f, stiffness = 260f),
                    label = "lineScale",
                )
                // Lines further from the current one get softly blurred (Android 12+).
                val blur by animateDpAsState(
                    when (distance) {
                        0 -> 0.dp
                        1 -> 1.5.dp
                        2 -> 2.5.dp
                        else -> 3.5.dp
                    },
                    tween(500),
                    label = "lineBlur",
                )
                // Active line rises a little into place.
                val lift by animateFloatAsState(if (isActive) 0f else 6f, spring(dampingRatio = 0.6f), label = "lineLift")
                Text(
                    line.text.ifBlank { "♪" },
                    color = color,
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { PlayerConnection.seekTo(line.timeMs) }
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationY = lift * density
                            transformOrigin = TransformOrigin(0f, 0.5f)
                        }
                        .blur(blur, BlurredEdgeTreatment.Unbounded)
                        .padding(horizontal = 24.dp, vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun PlainLyrics(text: String) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Text(
            "Без синхронизации",
            color = Color.White.copy(alpha = 0.6f),
            style = MaterialTheme.typography.labelLarge,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text,
            color = Color.White,
            style = MaterialTheme.typography.titleLargeEmphasized,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(120.dp))
    }
}

// ---------- Idle screen ----------

/**
 * After 20 seconds without touches (app open, music loaded) the screen goes dark
 * and shows the current lyric line. A tap brings everything back.
 */
@Composable
fun IdleOverlay(content: @Composable () -> Unit) {
    var lastTouch by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var idle by remember { mutableStateOf(false) }
    val s by PlayerConnection.state.collectAsState()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val lifecycleState by lifecycle.currentStateFlow.collectAsState()
    val resumed = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)

    LaunchedEffect(lastTouch, s.hasMedia, resumed) {
        if (!resumed) {
            idle = false
            return@LaunchedEffect
        }
        if (!s.hasMedia || idle) return@LaunchedEffect
        delay(20_000)
        idle = true
    }

    Box(
        Modifier.fillMaxSize().pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    awaitPointerEvent(PointerEventPass.Initial)
                    if (!idle) lastTouch = System.currentTimeMillis()
                }
            }
        }
    ) {
        content()
        AnimatedVisibility(
            visible = idle,
            enter = fadeIn(tween(450)),
            exit = fadeOut(tween(350)),
        ) {
            IdleScreen(
                onWake = {
                    idle = false
                    lastTouch = System.currentTimeMillis()
                },
                enterModifier = Modifier.animateEnterExit(
                    enter = slideInVertically(spring(dampingRatio = 0.8f, stiffness = 200f)) { it / 8 } +
                        fadeIn(tween(400, delayMillis = 150)),
                    exit = fadeOut(tween(200)),
                ),
            )
        }
    }
}

@Composable
private fun IdleScreen(onWake: () -> Unit, enterModifier: Modifier) {
    val s by PlayerConnection.state.collectAsState()
    val allLyrics by Lyrics.state.collectAsState()
    val lyrics = Lyrics.stateFor(allLyrics, s.mediaId)
    val pos by rememberPosition()
    val context = LocalContext.current

    // Screen brightness stays as it is; only keep the screen awake while music plays.
    DisposableEffect(s.isPlaying) {
        val window = (context as? Activity)?.window
        if (s.isPlaying) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    Box(
        Modifier.fillMaxSize()
            .background(Color.Black)
            // Swallow every touch so nothing underneath reacts; wake up when the finger lifts.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        event.changes.forEach { it.consume() }
                        if (event.changes.none { it.pressed }) onWake()
                    }
                }
            },
    ) {
        val live by Settings.liveBackground.collectAsState()
        if (live) {
            val palette = rememberCoverPalette(s.coverLarge ?: s.coverSmall, listOf(Color(0xFF4C2C8F), Color(0xFF0E4A7A)))
            LiveBackground(colors = palette, base = Color.Black, intensity = 0.45f, playing = s.isPlaying)
        }
        // Text slowly wanders a few dp so no pixel stays lit in one place (OLED burn-in).
        val drift = rememberInfiniteTransition(label = "drift")
        val dx by drift.animateFloat(-10f, 10f, infiniteRepeatable(tween(47_000), RepeatMode.Reverse), label = "dx")
        val dy by drift.animateFloat(-14f, 14f, infiniteRepeatable(tween(61_000), RepeatMode.Reverse), label = "dy")
        Column(
            enterModifier.fillMaxSize().offset(dx.dp, dy.dp).padding(horizontal = 32.dp, vertical = 48.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Spacer(Modifier.weight(1f))
            val synced = (lyrics as? LyricsState.Ready)?.synced
            if (synced != null) {
                val active = activeLine(synced, pos)
                AnimatedContent(
                    targetState = active,
                    transitionSpec = {
                        (slideInVertically(tween(300)) { it / 2 } + fadeIn(tween(300))) togetherWith
                            (slideOutVertically(tween(250)) { -it / 2 } + fadeOut(tween(200)))
                    },
                    label = "idleLine",
                ) { i ->
                    Column {
                        Text(
                            synced.getOrNull(i)?.text?.ifBlank { "♪" } ?: "♪",
                            color = Color.White,
                            style = MaterialTheme.typography.displaySmallEmphasized,
                            fontWeight = FontWeight.Bold,
                        )
                        synced.getOrNull(i + 1)?.let {
                            Spacer(Modifier.height(16.dp))
                            Text(
                                it.text.ifBlank { "♪" },
                                color = Color.White.copy(alpha = 0.3f),
                                style = MaterialTheme.typography.headlineSmallEmphasized,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            } else {
                Text(s.title, color = Color.White, style = MaterialTheme.typography.displaySmallEmphasized, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(s.artist, color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.headlineSmall)
            }
            Spacer(Modifier.weight(1f))

            Row(verticalAlignment = Alignment.CenterVertically) {
                TdImage(s.coverSmall, null, Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))) {
                    LetterCover(s.title, Modifier.fillMaxSize(), small = true)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(s.title, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodyLargeEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(s.artist, color = Color.White.copy(alpha = 0.45f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(12.dp))
            val progress = if (s.durationMs > 0) (pos.toFloat() / s.durationMs).coerceIn(0f, 1f) else 0f
            LinearWavyProgressIndicator(
                progress = { progress },
                amplitude = { if (s.isPlaying) 1f else 0f },
                color = Color.White.copy(alpha = 0.7f),
                trackColor = Color.White.copy(alpha = 0.15f),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Нажми, чтобы вернуться",
                color = Color.White.copy(alpha = 0.3f),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}
