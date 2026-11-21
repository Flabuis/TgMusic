package com.flabuis.tgmusic.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.flabuis.tgmusic.data.Channel
import com.flabuis.tgmusic.data.Library
import com.flabuis.tgmusic.data.Track
import com.flabuis.tgmusic.player.PlayerConnection
import com.flabuis.tgmusic.td.Td
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi

private sealed interface Screen {
    data object Home : Screen
    data object Picker : Screen
    data class Playlist(val channel: Channel) : Screen
    data object NowPlaying : Screen
}

@Composable
fun MainApp() {
    var stack by remember { mutableStateOf(listOf<Screen>(Screen.Home)) }
    val current = stack.last()
    val push: (Screen) -> Unit = { stack = stack + it }
    val pop: () -> Unit = { if (stack.size > 1) stack = stack.dropLast(1) }
    BackHandler(enabled = stack.size > 1) { pop() }

    // Nothing picked yet: go straight to the channel picker.
    val selected by Library.selected.collectAsState()
    LaunchedEffect(Unit) {
        if (selected.isEmpty()) stack = listOf(Screen.Home, Screen.Picker)
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            when (current) {
                Screen.Home -> HomeScreen(onOpen = { push(Screen.Playlist(it)) }, onPick = { push(Screen.Picker) })
                Screen.Picker -> PickerScreen(onDone = pop)
                is Screen.Playlist -> PlaylistScreen(current.channel, onBack = pop)
                Screen.NowPlaying -> NowPlayingScreen(onClose = pop)
            }
        }
        if (current != Screen.NowPlaying) MiniPlayer(onOpen = { push(Screen.NowPlaying) })
    }
}

// ---------- Home ----------

@Composable
private fun HomeScreen(onOpen: (Channel) -> Unit, onPick: () -> Unit) {
    val channels by Library.selected.collectAsState()
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    var shuffling by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TopBar("TgMusic") {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Меню") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Выбрать чаты с музыкой") }, onClick = { menu = false; onPick() })
                    DropdownMenuItem(text = { Text("Выйти из аккаунта") }, onClick = {
                        menu = false
                        PlayerConnection.stop()
                        Library.clear()
                        Td.sendAsync(TdApi.LogOut())
                    })
                }
            }
        }

        if (channels.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Плейлистов пока нет", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text("Выбери каналы или группы, где лежит музыка", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                Button(onClick = onPick) { Text("Выбрать чаты с музыкой") }
            }
            return@Column
        }

        Button(
            onClick = {
                shuffling = true
                scope.launch {
                    try {
                        val all = channels.flatMap { Library.loadTracks(it.id) }.distinctBy { it.fileId }
                        if (all.isNotEmpty()) PlayerConnection.playQueue(all, all.indices.random(), shuffle = true)
                    } catch (_: Exception) {
                    } finally {
                        shuffling = false
                    }
                }
            },
            enabled = !shuffling,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).height(52.dp),
        ) {
            if (shuffling) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            else {
                Icon(AppIcons.Shuffle, null)
                Spacer(Modifier.width(8.dp))
                Text("Перемешать всё")
            }
        }

        LazyColumn(contentPadding = PaddingValues(bottom = 8.dp)) {
            items(channels, key = { it.id }) { ch ->
                Row(
                    Modifier.fillMaxWidth().clickable { onOpen(ch) }.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Cover(ch.title, Modifier.size(56.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(ch.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val count = Library.cachedTracks(ch.id)?.size
                        Text(
                            if (count != null) "$count ${tracksWord(count)}" else "Плейлист",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

// ---------- Channel picker ----------

@Composable
private fun PickerScreen(onDone: () -> Unit) {
    var channels by remember { mutableStateOf<List<Channel>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val picked = remember { mutableStateListOf<Long>().apply { addAll(Library.selected.value.map { it.id }) } }

    LaunchedEffect(Unit) {
        try {
            channels = Library.loadMyChannels()
        } catch (e: Exception) {
            error = e.message
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("Каналы и группы", onBack = onDone)
        Text(
            "Отметь, где лежит музыка — каждый чат станет плейлистом",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        val list = channels
        when {
            error != null -> MessageScreen("Не получилось загрузить каналы: $error")
            list == null -> LoadingScreen("Ищу твои каналы и группы…")
            list.isEmpty() -> MessageScreen("Не нашёл каналов и групп, где ты владелец или админ")
            else -> {
                LazyColumn(Modifier.weight(1f)) {
                    items(list, key = { it.id }) { ch ->
                        val checked = ch.id in picked
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable { if (checked) picked.remove(ch.id) else picked.add(ch.id) }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = checked, onCheckedChange = { if (it) picked.add(ch.id) else picked.remove(ch.id) })
                            Column(Modifier.weight(1f)) {
                                Text(ch.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    (if (ch.isGroup) "группа" else "канал") + if (ch.isOwner) " · твоя" else " · ты админ",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                Button(
                    onClick = {
                        Library.setSelected(list.filter { it.id in picked })
                        onDone()
                    },
                    modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp),
                ) { Text("Готово (${picked.size})") }
            }
        }
    }
}

// ---------- Playlist ----------

@Composable
private fun PlaylistScreen(channel: Channel, onBack: () -> Unit) {
    var tracks by remember { mutableStateOf(Library.cachedTracks(channel.id)) }
    var loading by remember { mutableStateOf(tracks == null) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var reload by remember { mutableStateOf(0) }
    val player by PlayerConnection.state.collectAsState()

    LaunchedEffect(channel.id, reload) {
        if (tracks != null && reload == 0) return@LaunchedEffect
        loading = true
        error = null
        try {
            tracks = Library.loadTracks(channel.id, force = reload > 0)
        } catch (e: Exception) {
            error = e.message
        }
        loading = false
    }

    val all = tracks.orEmpty()
    val shown = if (query.isBlank()) all else all.filter {
        it.title.contains(query, ignoreCase = true) || it.artist.contains(query, ignoreCase = true)
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(channel.title, onBack = onBack) {
            IconButton(onClick = { searching = !searching; if (!searching) query = "" }) {
                Icon(Icons.Default.Search, "Поиск")
            }
            IconButton(onClick = { reload++ }, enabled = !loading) {
                Icon(Icons.Default.Refresh, "Обновить")
            }
        }
        if (searching) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Название или исполнитель") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        when {
            loading && all.isEmpty() -> LoadingScreen("Собираю треки…")
            error != null && all.isEmpty() -> MessageScreen("Ошибка: $error")
            all.isEmpty() -> MessageScreen("Здесь нет аудио")
            else -> {
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(onClick = { PlayerConnection.playQueue(shown, 0) }, modifier = Modifier.weight(1f)) {
                        Icon(AppIcons.Play, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Слушать")
                    }
                    FilledTonalButton(
                        onClick = { if (shown.isNotEmpty()) PlayerConnection.playQueue(shown, shown.indices.random(), shuffle = true) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(AppIcons.Shuffle, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Вперемешку")
                    }
                }
                Text(
                    "${shown.size} ${tracksWord(shown.size)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                LazyColumn(Modifier.weight(1f)) {
                    itemsIndexed(shown, key = { _, t -> t.mediaId }) { index, t ->
                        TrackRow(t, isCurrent = t.mediaId == player.mediaId) {
                            PlayerConnection.playQueue(shown, index, shuffle = player.shuffle)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackRow(track: Track, isCurrent: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(10.dp))
                .background(if (isCurrent) Accent else MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(AppIcons.Note, null, tint = if (isCurrent) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isCurrent) Accent else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
            )
            Text(
                track.artist,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (track.duration > 0) {
            Text(
                formatTime(track.duration * 1000L),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---------- Player ----------

@Composable
private fun rememberPosition(): State<Long> {
    val pos = remember { mutableLongStateOf(PlayerConnection.positionMs) }
    LaunchedEffect(Unit) {
        while (true) {
            pos.longValue = PlayerConnection.positionMs
            delay(500)
        }
    }
    return pos
}

@Composable
private fun MiniPlayer(onOpen: () -> Unit) {
    val s by PlayerConnection.state.collectAsState()
    if (!s.hasMedia) return
    val pos by rememberPosition()

    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        LinearProgressIndicator(
            progress = { if (s.durationMs > 0) (pos.toFloat() / s.durationMs).coerceIn(0f, 1f) else 0f },
            modifier = Modifier.fillMaxWidth().height(2.dp),
            drawStopIndicator = {},
        )
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Cover(s.title, Modifier.size(44.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(s.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text(
                    s.error ?: s.artist,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (s.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PlayPauseButton(s.isPlaying, s.isBuffering, size = 44)
            IconButton(onClick = PlayerConnection::next) { Icon(AppIcons.Next, "Дальше") }
        }
    }
}

@Composable
private fun PlayPauseButton(isPlaying: Boolean, isBuffering: Boolean, size: Int) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(Accent).clickable { PlayerConnection.togglePlay() },
        contentAlignment = Alignment.Center,
    ) {
        if (isBuffering && !isPlaying) {
            CircularProgressIndicator(Modifier.size((size / 2).dp), color = Color.White, strokeWidth = 2.dp)
        } else {
            Icon(
                if (isPlaying) AppIcons.Pause else AppIcons.Play,
                if (isPlaying) "Пауза" else "Играть",
                tint = Color.White,
                modifier = Modifier.size((size / 2).dp),
            )
        }
    }
}

@Composable
private fun NowPlayingScreen(onClose: () -> Unit) {
    val s by PlayerConnection.state.collectAsState()
    val pos by rememberPosition()
    var dragging by remember { mutableStateOf<Float?>(null) }

    LaunchedEffect(s.hasMedia) { if (!s.hasMedia) onClose() }

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(AppIcons.Down, "Свернуть") }
            Text(
                "Сейчас играет",
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        Spacer(Modifier.height(16.dp))
        Cover(
            s.title,
            Modifier.weight(1f, fill = false)
                .aspectRatio(1f, matchHeightConstraintsFirst = true)
                .align(Alignment.CenterHorizontally),
            iconSize = 120,
        )
        Spacer(Modifier.height(28.dp))
        Text(s.title, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        Text(s.artist, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        s.error?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(16.dp))

        val duration = s.durationMs.coerceAtLeast(1)
        Slider(
            value = dragging ?: (pos.toFloat() / duration).coerceIn(0f, 1f),
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let { PlayerConnection.seekTo((it * duration).toLong()) }
                dragging = null
            },
            enabled = s.durationMs > 0,
        )
        Row(Modifier.fillMaxWidth()) {
            val shownPos = dragging?.let { (it * duration).toLong() } ?: pos
            Text(formatTime(shownPos), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text(formatTime(s.durationMs), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToggleIcon(AppIcons.Shuffle, "Перемешать", s.shuffle, PlayerConnection::toggleShuffle)
            IconButton(onClick = PlayerConnection::previous, modifier = Modifier.size(56.dp)) {
                Icon(AppIcons.Previous, "Назад", Modifier.size(36.dp))
            }
            PlayPauseButton(s.isPlaying, s.isBuffering, size = 72)
            IconButton(onClick = PlayerConnection::next, modifier = Modifier.size(56.dp)) {
                Icon(AppIcons.Next, "Дальше", Modifier.size(36.dp))
            }
            ToggleIcon(
                if (s.repeatMode == Player.REPEAT_MODE_ONE) AppIcons.RepeatOne else AppIcons.Repeat,
                "Повтор",
                s.repeatMode != Player.REPEAT_MODE_OFF,
                PlayerConnection::cycleRepeat,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ToggleIcon(icon: ImageVector, description: String, active: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(icon, description, tint = if (active) Accent else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------- Shared bits ----------

@Composable
private fun TopBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
        } else {
            Spacer(Modifier.width(12.dp))
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        actions()
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
}

/** Placeholder cover: a gradient picked from the name, with a note on top. */
@Composable
private fun Cover(seed: String, modifier: Modifier, iconSize: Int = 24) {
    val palette = listOf(
        Color(0xFF8B5CF6) to Color(0xFF22D3EE),
        Color(0xFFF43F5E) to Color(0xFFF59E0B),
        Color(0xFF10B981) to Color(0xFF3B82F6),
        Color(0xFFEC4899) to Color(0xFF8B5CF6),
        Color(0xFF6366F1) to Color(0xFF14B8A6),
    )
    val (a, b) = palette[Math.floorMod(seed.hashCode(), palette.size)]
    Box(
        modifier.clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(listOf(a, b))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(AppIcons.Note, null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(iconSize.dp))
    }
}

private fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}

private fun tracksWord(n: Int): String {
    val mod100 = n % 100
    val mod10 = n % 10
    return when {
        mod100 in 11..14 -> "треков"
        mod10 == 1 -> "трек"
        mod10 in 2..4 -> "трека"
        else -> "треков"
    }
}
