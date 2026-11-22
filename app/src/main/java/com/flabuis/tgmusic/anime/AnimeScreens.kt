@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package com.flabuis.tgmusic.anime

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import androidx.annotation.OptIn as AndroidOptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import com.flabuis.tgmusic.player.PlayerConnection
import com.flabuis.tgmusic.td.TgDataSource
import com.flabuis.tgmusic.ui.AppIcons
import com.flabuis.tgmusic.ui.LetterCover
import com.flabuis.tgmusic.ui.TdImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ---------- Catalog / search ----------

@Composable
fun AnimeHomeScreen(onBack: () -> Unit, onOpen: (AnimeResult, WatchEntry?) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<AnimeResult>?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var resuming by remember { mutableStateOf<String?>(null) }
    val history by AnimeHistory.entries.collectAsState()
    val scope = rememberCoroutineScope()

    fun runSearch(q: String) {
        if (q.isBlank()) return
        loading = true
        error = null
        scope.launch {
            results = try { AnimeBot.search(q.trim()) } catch (e: Exception) { error = e.message; null }
            loading = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 8.dp)) {
            IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
            }
            Text("Аниме", style = MaterialTheme.typography.headlineMediumEmphasized)
            Spacer(Modifier.width(8.dp))
            Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.tertiary) {
                Text(
                    "BETA",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onTertiary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Найти аниме") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { runSearch(query) }),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )

        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 12.dp)) {
            if (results == null && history.isNotEmpty()) {
                item {
                    Text(
                        "Продолжить просмотр",
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(history, key = { it.title }) { e ->
                            ContinueCard(e, busy = resuming == e.title) {
                                resuming = e.title
                                scope.launch {
                                    try {
                                        val found = AnimeBot.search(e.query)
                                        val match = found.firstOrNull { it.title == e.title } ?: found.firstOrNull()
                                        if (match != null) onOpen(match, e) else error = "Бот не нашёл «${e.title}»"
                                    } catch (ex: Exception) {
                                        error = ex.message
                                    }
                                    resuming = null
                                }
                            }
                        }
                    }
                }
            }
            when {
                loading -> item {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        LoadingIndicator(Modifier.size(64.dp))
                    }
                }
                error != null -> item { Hint("Не получилось: $error") }
                results?.isEmpty() == true -> item { Hint("Ничего не нашлось") }
                results == null && history.isEmpty() -> item {
                    Hint("Ищи по названию. Каталог работает через бота @${AnimeBot.USERNAME}, видео идёт через Telegram.")
                }
            }
            items(results.orEmpty(), key = { it.resultId }) { r ->
                ResultRow(r) { onOpen(r, AnimeHistory.get(r.title)) }
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(32.dp),
    )
}

@Composable
private fun ContinueCard(e: WatchEntry, busy: Boolean, onClick: () -> Unit) {
    Column(Modifier.width(140.dp).clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.7f).clip(RoundedCornerShape(20.dp))) {
            TdImage(e.thumbFileId, null, Modifier.fillMaxSize()) { LetterCover(e.title, Modifier.fillMaxSize()) }
            if (busy) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                    LoadingIndicator(Modifier.size(48.dp), color = Color.White)
                }
            }
            val progress = if (e.durationMs > 0) (e.positionMs.toFloat() / e.durationMs).coerceIn(0f, 1f) else 0f
            LinearWavyProgressIndicator(
                progress = { progress },
                amplitude = { 0f },
                modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(8.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(e.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMediumEmphasized)
        Text(
            "Серия ${e.episode}" + (e.total?.let { " из $it" } ?: ""),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ResultRow(r: AnimeResult, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(72.dp).aspectRatio(0.7f).clip(RoundedCornerShape(14.dp))) {
            TdImage(r.thumbFileId, null, Modifier.fillMaxSize()) { LetterCover(r.title, Modifier.fillMaxSize()) }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(r.title, style = MaterialTheme.typography.titleMediumEmphasized, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                r.rating?.let { Chip("★ $it") }
                r.year?.let { Chip(it) }
                r.episodes?.let { Chip("$it сер.") }
            }
        }
    }
}

@Composable
private fun Chip(text: String) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

// ---------- Anime page ----------

@Composable
fun AnimePageScreen(
    result: AnimeResult,
    resume: WatchEntry?,
    onBack: () -> Unit,
    onWatch: (card: BotMessage, resume: WatchEntry?) -> Unit,
) {
    var card by remember { mutableStateOf<BotMessage?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(result.resultId) {
        try {
            card = AnimeBot.open(result)
        } catch (e: Exception) {
            error = e.message
        }
    }

    Column(Modifier.fillMaxSize()) {
        BackBar(onBack)
        val c = card
        when {
            error != null -> BotProblem(error!!)
            c == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    LoadingIndicator(Modifier.size(64.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Открываю «${result.title}»…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            else -> Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                Box(
                    Modifier.fillMaxWidth(0.6f).aspectRatio(0.7f).align(Alignment.CenterHorizontally)
                        .clip(RoundedCornerShape(28.dp)),
                ) {
                    TdImage(c.photoFileId ?: result.thumbFileId, null, Modifier.fillMaxSize(), fallbackId = result.thumbFileId) {
                        LetterCover(result.title, Modifier.fillMaxSize())
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    result.title,
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
                    result.rating?.let { Chip("★ $it") }
                    result.year?.let { Chip(it) }
                    result.episodes?.let { Chip("$it сер.") }
                }
                Spacer(Modifier.height(20.dp))
                if (resume != null) {
                    Button(
                        onClick = { onWatch(c, resume) },
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.fillMaxWidth().height(60.dp),
                    ) {
                        Icon(AppIcons.Play, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Продолжить: серия ${resume.episode}", style = MaterialTheme.typography.titleMediumEmphasized)
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Button(
                    onClick = { onWatch(c, null) },
                    shapes = ButtonDefaults.shapes(),
                    colors = if (resume != null) ButtonDefaults.filledTonalButtonColors() else ButtonDefaults.buttonColors(),
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                ) {
                    Icon(AppIcons.Play, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Смотреть", style = MaterialTheme.typography.titleMediumEmphasized)
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    c.text.lines().filterNot { it.contains("VPN", true) || it.contains("Код аниме", true) }.joinToString("\n").trim(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun BackBar(onBack: () -> Unit, title: String = "") {
    Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
        }
        Text(title, style = MaterialTheme.typography.titleMediumEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** When the bot answers something unexpected: explain, show what it said, offer to open it. */
@Composable
private fun BotProblem(message: String) {
    val context = LocalContext.current
    var showRaw by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(message, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        FilledTonalButton(onClick = { showRaw = true }) { Text("Что ответил бот") }
        TextButton(onClick = {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/${AnimeBot.USERNAME}")))
        }) { Text("Открыть бота в Telegram") }
    }
    if (showRaw) {
        val raw = AnimeBot.lastRaw
        AlertDialog(
            onDismissRequest = { showRaw = false },
            confirmButton = { TextButton(onClick = { showRaw = false }) { Text("Ок") } },
            title = { Text("Последний ответ бота") },
            text = {
                Text(
                    if (raw == null) "Бот ничего не прислал"
                    else raw.text + "\n\nКнопки:\n" + raw.buttons.joinToString("\n") { row -> row.joinToString(" | ") { it.text } } +
                        "\n\nВидео: " + (if (raw.video != null) "есть" else "нет"),
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            },
        )
    }
}

// ---------- Video player ----------

@AndroidOptIn(UnstableApi::class)
@Composable
fun AnimeVideoScreen(
    title: String,
    thumbFileId: Int?,
    card: BotMessage,
    resume: WatchEntry?,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<EpisodeState?>(null) }
    var busy by remember { mutableStateOf<String?>("Открываю серию…") }
    var error by remember { mutableStateOf<String?>(null) }
    var fullscreen by remember { mutableStateOf(false) }
    var voices by remember { mutableStateOf<BotMessage?>(null) }
    var position by remember { mutableLongStateOf(0L) }
    var pendingSeek by remember { mutableLongStateOf(-1L) }
    val history by AnimeHistory.entries.collectAsState()
    val watched = history.firstOrNull { it.title == title }?.watched.orEmpty()

    val player = remember {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(ProgressiveMediaSource.Factory(TgDataSource.Factory()))
            // start after ~0.7 s of video instead of the default 2.5 s
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(5_000, 30_000, 700, 1_500)
                    .build()
            )
            .build()
    }

    fun saveProgress() {
        val st = state ?: return
        val ep = st.episode ?: return
        val dur = player.duration.takeIf { it > 0 } ?: 0L
        val pos = player.currentPosition
        AnimeHistory.save(
            WatchEntry(
                title = title, query = title, thumbFileId = thumbFileId,
                episode = ep, total = st.total, positionMs = pos, durationMs = dur,
                updated = System.currentTimeMillis(),
                watched = if (dur > 0 && pos > dur * 0.85) setOf(ep) else emptySet(),
            )
        )
    }

    /** Press a bot button and wait for a different video. */
    suspend fun switchVideo(button: BotButton, label: String) {
        val st = state ?: return
        busy = label
        try {
            saveProgress()
            val old = st.video?.video?.id
            state = EpisodeState(AnimeBot.press(st.msg.id, button) { it.video != null && it.video.video.id != old })
        } catch (e: Exception) {
            error = e.message
        }
        busy = null
    }

    /** Walks the bot's episode pages until episode [n] can be pressed. */
    suspend fun goToEpisode(n: Int) {
        repeat(20) {
            val st = state ?: return
            if (st.episode == n) return
            st.episodes.firstOrNull { it.first == n }?.let { (_, b) -> switchVideo(b, "Серия $n…"); return }
            val pageMax = st.episodes.maxOfOrNull { it.first } ?: return
            val pageButton = (if (n > pageMax) st.nextPage else st.prevPage) ?: return
            busy = "Ищу серию $n…"
            state = EpisodeState(AnimeBot.press(st.msg.id, pageButton))
        }
    }

    DisposableEffect(Unit) {
        PlayerConnection.pause() // music off while watching
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    val next = state?.nextEpisode
                    if (next != null) scope.launch { switchVideo(next, "Следующая серия…") }
                }
            }
        }
        player.addListener(listener)
        onDispose {
            saveProgress()
            player.removeListener(listener)
            player.release()
            (context as? Activity)?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            (context as? Activity)?.window?.let {
                WindowCompat.getInsetsController(it, view).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // First episode: whatever the bot opens, then jump to the saved one.
    LaunchedEffect(Unit) {
        try {
            val first = AnimeBot.watch(card)
            if (first.video == null) {
                // The bot wants a dubbing first: show the list, continue after the pick.
                voices = first
                busy = null
                return@LaunchedEffect
            }
            state = EpisodeState(first)
            if (resume != null && state?.episode != resume.episode) goToEpisode(resume.episode)
            if (resume != null && state?.episode == resume.episode) pendingSeek = resume.positionMs
        } catch (e: Exception) {
            error = e.message
        }
        busy = null
    }

    // The video may arrive a moment before its buttons: re-read the message shortly after.
    LaunchedEffect(state?.msg?.id, state?.video?.video?.id) {
        val st = state ?: return@LaunchedEffect
        delay(700)
        val fresh = try { AnimeBot.message(st.msg.id) } catch (e: Exception) { null } ?: return@LaunchedEffect
        if (fresh.video?.video?.id == st.video?.video?.id && state === st) state = EpisodeState(fresh)
    }

    // New video from the bot -> play it.
    val videoId = state?.video?.video?.id
    LaunchedEffect(videoId) {
        val id = videoId ?: return@LaunchedEffect
        player.setMediaItem(MediaItem.fromUri(TgDataSource.uriFor(id)))
        player.prepare()
        if (pendingSeek > 0) {
            player.seekTo(pendingSeek)
            pendingSeek = -1
        }
        player.play()
    }

    // Position for the "skip opening" button + saving progress.
    LaunchedEffect(Unit) {
        var tick = 0
        while (true) {
            delay(500)
            position = player.currentPosition
            if (++tick % 10 == 0 && player.isPlaying) saveProgress()
        }
    }

    // Fullscreen: landscape, no system bars.
    LaunchedEffect(fullscreen) {
        val activity = context as? Activity ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(activity.window, view)
        if (fullscreen) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    val st = state
    Column(Modifier.fillMaxSize().background(if (fullscreen) Color.Black else Color.Transparent)) {
        if (!fullscreen) BackBar(onBack, title)

        // Video + overlays
        Box(
            (if (fullscreen) Modifier.fillMaxSize() else Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                .background(Color.Black),
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                        setShowNextButton(false)
                        setShowPreviousButton(false)
                        setFullscreenButtonClickListener { full -> fullscreen = full }
                    }
                },
                update = { it.setFullscreenButtonState(fullscreen) },
                modifier = Modifier.fillMaxSize(),
            )
            val opening = st?.opening
            if (opening != null && position / 1000 in opening.first until opening.last - 2) {
                Button(
                    onClick = { player.seekTo(opening.last * 1000L) },
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp, 56.dp),
                ) { Text("Пропустить опенинг") }
            }
            busy?.let {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        LoadingIndicator(Modifier.size(56.dp), color = Color.White)
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = Color.White)
                    }
                }
            }
        }
        if (fullscreen) return@Column

        if (error != null) {
            BotProblem(error!!)
            return@Column
        }
        if (st == null) return@Column

        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text(
                "Серия ${st.episode ?: "?"}" + (st.total?.let { " из $it" } ?: ""),
                style = MaterialTheme.typography.titleLargeEmphasized,
            )
            Text(
                listOfNotNull(st.voice, st.quality).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                st.nextEpisode?.let { next ->
                    Button(
                        onClick = { scope.launch { switchVideo(next, "Следующая серия…") } },
                        enabled = busy == null,
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Icon(AppIcons.Next, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Следующая")
                    }
                }
                st.changeVoice?.let { b ->
                    FilledTonalButton(
                        onClick = {
                            scope.launch {
                                busy = "Загружаю озвучки…"
                                voices = try { AnimeBot.press(st.msg.id, b) } catch (e: Exception) { error = e.message; null }
                                busy = null
                            }
                        },
                        enabled = busy == null,
                        shapes = ButtonDefaults.shapes(),
                    ) { Text("Озвучка") }
                }
            }

            if (st.qualities.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text("Качество", style = MaterialTheme.typography.titleSmallEmphasized)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    st.qualities.forEach { (q, b, selected) ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable(enabled = !selected && busy == null) {
                                scope.launch {
                                    pendingSeek = player.currentPosition
                                    switchVideo(b, "Меняю качество на ${q}p…")
                                }
                            },
                        ) {
                            Text(
                                "${q}p",
                                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            }

            if (st.episodes.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Серии", style = MaterialTheme.typography.titleSmallEmphasized, modifier = Modifier.weight(1f))
                    st.prevPage?.let { b ->
                        TextButton(onClick = { scope.launch { busy = "…"; state = runCatching { EpisodeState(AnimeBot.press(st.msg.id, b)) }.getOrDefault(st); busy = null } }) { Text("‹ назад") }
                    }
                    st.pageLabel?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    st.nextPage?.let { b ->
                        TextButton(onClick = { scope.launch { busy = "…"; state = runCatching { EpisodeState(AnimeBot.press(st.msg.id, b)) }.getOrDefault(st); busy = null } }) { Text("дальше ›") }
                    }
                }
                Spacer(Modifier.height(6.dp))
                st.episodes.chunked(5).forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { (n, b) ->
                            val current = n == st.episode
                            val seen = n in watched
                            Box(
                                Modifier.weight(1f).height(48.dp)
                                    .clip(if (current) MaterialShapes.Cookie9Sided.toShape() else RoundedCornerShape(14.dp))
                                    .background(
                                        when {
                                            current -> MaterialTheme.colorScheme.primary
                                            seen -> MaterialTheme.colorScheme.secondaryContainer
                                            else -> MaterialTheme.colorScheme.surfaceContainerHigh
                                        }
                                    )
                                    .clickable(enabled = !current && busy == null) { scope.launch { switchVideo(b, "Серия $n…") } },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    n.toString(),
                                    color = if (current) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.titleMediumEmphasized,
                                )
                            }
                        }
                        repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    // Voice picker: the bot's list of dubbing studios.
    voices?.let { list ->
        val options = list.voiceOptions
        val raw = if (options.isEmpty()) list.allButtons.filter { b ->
            b.data != null && !b.has("назад", "Вернуться", "К аниме", "Главн", "Поиск", "Донат", "Mod", "Настро", "➡", "⬅", "/")
        } else emptyList()
        fun pick(button: BotButton) {
            voices = null
            scope.launch {
                busy = "Включаю озвучку…"
                try {
                    val old = state?.video?.video?.id
                    state = EpisodeState(AnimeBot.press(list.id, button) { it.video != null && it.video.video.id != old })
                    val target = resume?.episode
                    if (target != null && state?.episode != target) goToEpisode(target)
                    if (resume != null && state?.episode == resume.episode && player.currentPosition < 1000) pendingSeek = resume.positionMs
                } catch (e: Exception) {
                    error = e.message
                }
                busy = null
            }
        }
        AlertDialog(
            onDismissRequest = { if (state != null) voices = null },
            confirmButton = {
                TextButton(onClick = { voices = null; if (state == null) onBack() }) { Text("Отмена") }
            },
            title = { Text("Выбери озвучку") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    options.forEach { o ->
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable { pick(o.button) },
                        ) {
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                Text(o.name, style = MaterialTheme.typography.titleMediumEmphasized)
                                Text(
                                    listOfNotNull(o.episodes?.let { "серии $it" }, o.views?.let { "👁 $it" }, o.qualities).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    raw.forEach { b ->
                        Text(
                            b.text,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.fillMaxWidth().clip(CircleShape).clickable { pick(b) }
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                        )
                    }
                }
            },
        )
    }
}
