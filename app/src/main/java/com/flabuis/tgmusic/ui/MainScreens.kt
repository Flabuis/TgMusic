@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package com.flabuis.tgmusic.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.unit.DpOffset
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.ripple
import androidx.compose.ui.input.pointer.pointerInput
import com.flabuis.tgmusic.data.Downloads
import com.flabuis.tgmusic.data.Settings
import com.flabuis.tgmusic.data.Updater
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.flabuis.tgmusic.data.Channel
import com.flabuis.tgmusic.data.Library
import com.flabuis.tgmusic.data.Track
import com.flabuis.tgmusic.player.PlayerConnection
import com.flabuis.tgmusic.td.Td
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private sealed interface Screen {
    data object Home : Screen
    data object Picker : Screen
    data class Playlist(val channel: Channel) : Screen
    data object NowPlaying : Screen
    data object Settings : Screen
    data object Lyrics : Screen
}

@Composable
fun MainApp() {
    var stack by remember { mutableStateOf(listOf<Screen>(Screen.Home)) }
    val current = stack.last()
    val push: (Screen) -> Unit = { stack = stack + it }
    val pop: () -> Unit = { if (stack.size > 1) stack = stack.dropLast(1) }
    BackHandler(enabled = stack.size > 1) { pop() }

    LaunchedEffect(Unit) { Updater.checkInBackground() }

    // Nothing picked yet: go straight to the picker.
    val selected by Library.selected.collectAsState()
    LaunchedEffect(Unit) {
        if (selected.isEmpty()) stack = listOf(Screen.Home, Screen.Picker)
    }

    // Live background behind the whole app (home, playlists, settings), in the playing track's colors.
    val player by PlayerConnection.state.collectAsState()
    val live by Settings.liveBackground.collectAsState()
    if (live && player.hasMedia) {
        val palette = rememberCoverPalette(
            player.coverLarge ?: player.coverSmall,
            listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary),
        )
        LiveBackground(
            colors = palette,
            base = MaterialTheme.colorScheme.background,
            intensity = 0.5f,
            playing = player.isPlaying,
        )
    }

    Column(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = current,
            transitionSpec = {
                if (targetState == Screen.NowPlaying || targetState == Screen.Lyrics) {
                    (slideInVertically { it / 3 } + fadeIn()) togetherWith fadeOut()
                } else if (initialState == Screen.NowPlaying || initialState == Screen.Lyrics) {
                    fadeIn() togetherWith (slideOutVertically { it / 3 } + fadeOut())
                } else {
                    (slideInHorizontally { it / 5 } + fadeIn()) togetherWith fadeOut()
                }
            },
            modifier = Modifier.weight(1f),
            label = "screen",
        ) { screen ->
            when (screen) {
                Screen.Home -> HomeScreen(
                    onOpen = { push(Screen.Playlist(it)) },
                    onPick = { push(Screen.Picker) },
                    onSettings = { push(Screen.Settings) },
                )
                Screen.Picker -> PickerScreen(onDone = pop)
                is Screen.Playlist -> PlaylistScreen(screen.channel, onBack = pop)
                Screen.NowPlaying -> NowPlayingScreen(onClose = pop, onLyrics = { push(Screen.Lyrics) })
                Screen.Settings -> SettingsScreen(onBack = pop)
                Screen.Lyrics -> LyricsScreen(onClose = pop)
            }
        }
        if (current != Screen.NowPlaying && current != Screen.Lyrics) MiniPlayer(onOpen = { push(Screen.NowPlaying) })
    }
}

/** Expressive shapes for playlist avatars, picked per item. */
private val avatarShapes = listOf(
    MaterialShapes.Cookie9Sided,
    MaterialShapes.Clover4Leaf,
    MaterialShapes.Sunny,
    MaterialShapes.SoftBurst,
    MaterialShapes.Cookie6Sided,
    MaterialShapes.Flower,
    MaterialShapes.Pentagon,
    MaterialShapes.Cookie12Sided,
)

@Composable
private fun avatarShape(index: Int): Shape = avatarShapes[Math.floorMod(index, avatarShapes.size)].toShape()

// ---------- Home ----------

@Composable
private fun HomeScreen(onOpen: (Channel) -> Unit, onPick: () -> Unit, onSettings: () -> Unit) {
    val channels by Library.selected.collectAsState()
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    var shuffling by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        LargeFlexibleTopAppBar(
            title = { Text("TgMusic") },
            subtitle = {
                Text(if (channels.isEmpty()) "Музыка из твоего Telegram" else "${channels.size} ${playlistsWord(channels.size)}")
            },
            actions = {
                BluetoothButton()
                Box {
                    IconButton(onClick = { menu = true }, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.Default.MoreVert, "Меню")
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Выбрать чаты с музыкой") }, onClick = { menu = false; onPick() })
                        DropdownMenuItem(text = { Text("Настройки") }, onClick = { menu = false; onSettings() })
                        DropdownMenuItem(text = { Text("Выйти из аккаунта") }, onClick = {
                            menu = false
                            PlayerConnection.stop()
                            Library.clear()
                            Td.sendAsync(TdApi.LogOut())
                        })
                    }
                }
            },
        )

        if (channels.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(120.dp).clip(MaterialShapes.Cookie9Sided.toShape())
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(AppIcons.Note, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Spacer(Modifier.height(20.dp))
                Text("Плейлистов пока нет", style = MaterialTheme.typography.titleLargeEmphasized)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Выбери каналы или группы, где лежит музыка",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))
                Button(onClick = onPick, shapes = ButtonDefaults.shapes()) { Text("Выбрать чаты") }
            }
            return@Column
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(2) }) { UpdateBanner() }
            item(span = { GridItemSpan(2) }) {
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
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                ) {
                    if (shuffling) {
                        LoadingIndicator(Modifier.size(32.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Icon(AppIcons.Shuffle, null)
                        Spacer(Modifier.width(10.dp))
                        Text("Перемешать всё", style = MaterialTheme.typography.titleMediumEmphasized)
                    }
                }
            }
            itemsIndexed(channels, key = { _, ch -> ch.id }) { index, ch ->
                PlaylistCard(ch, avatarShape(index)) { onOpen(ch) }
            }
        }
    }
}

@Composable
private fun PlaylistCard(ch: Channel, shape: Shape, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).clickable(onClick = onClick).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ChatAvatar(ch, Modifier.fillMaxWidth().aspectRatio(1f), shape)
        Spacer(Modifier.height(10.dp))
        Text(
            ch.title,
            style = MaterialTheme.typography.titleMediumEmphasized,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val count = Library.cachedTracks(ch.id)?.size
        Text(
            if (count != null) "$count ${tracksWord(count)}" else if (ch.isGroup) "Группа" else "Канал",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** Telegram avatar of a chat, or a colored letter if it has none. */
@Composable
internal fun ChatAvatar(ch: Channel, modifier: Modifier, shape: Shape) {
    var photo by remember(ch.id) { mutableStateOf(Library.cachedPhoto(ch.id)) }
    LaunchedEffect(ch.id) { if (photo == null) photo = Library.chatPhoto(ch.id) }
    val p = photo
    TdImage(
        fileId = p?.big?.id ?: p?.small?.id,
        mini = p?.minithumbnail?.data,
        modifier = modifier.clip(shape),
    ) {
        LetterCover(ch.title, Modifier.fillMaxSize())
    }
}

// ---------- Picker ----------

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
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        val list = channels
        when {
            error != null -> MessageScreen("Не получилось загрузить: $error")
            list == null -> LoadingScreen("Ищу твои каналы и группы…")
            list.isEmpty() -> MessageScreen("Не нашёл каналов и групп, где ты владелец или админ")
            else -> {
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(list, key = { it.id }) { ch ->
                        val checked = ch.id in picked
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable { if (checked) picked.remove(ch.id) else picked.add(ch.id) }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ChatAvatar(ch, Modifier.size(48.dp), CircleShape)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(ch.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLargeEmphasized)
                                Text(
                                    (if (ch.isGroup) "группа" else "канал") + if (ch.isOwner) " · твоя" else " · ты админ",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Checkbox(checked = checked, onCheckedChange = { if (it) picked.add(ch.id) else picked.remove(ch.id) })
                        }
                    }
                }
                Button(
                    onClick = {
                        Library.setSelected(list.filter { it.id in picked })
                        onDone()
                    },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth().padding(16.dp).height(60.dp),
                ) { Text("Готово (${picked.size})", style = MaterialTheme.typography.titleMediumEmphasized) }
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
    var menu by remember { mutableStateOf(false) }
    val player by PlayerConnection.state.collectAsState()
    val haptic = LocalHapticFeedback.current

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
    val canReorder = query.isBlank()
    val shown = if (canReorder) all else all.filter {
        it.title.contains(query, ignoreCase = true) || it.artist.contains(query, ignoreCase = true)
    }

    var menuFor by remember { mutableStateOf<String?>(null) }
    var movedDuringDrag by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Track?>(null) }
    val scope = rememberCoroutineScope()

    deleting?.let { track ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Удалить трек?") },
            text = { Text("«${track.title}» удалится из «${channel.title}» в Telegram у всех. Вернуть не получится.") },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    scope.launch {
                        try {
                            PlayerConnection.removeFromQueue(track.mediaId)
                            Library.deleteTrack(track)
                            tracks = Library.cachedTracks(channel.id)
                        } catch (e: Exception) {
                            error = e.message
                        }
                    }
                }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Отмена") } },
        )
    }

    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val list = tracks ?: return@rememberReorderableLazyListState
        val fromIndex = list.indexOfFirst { it.mediaId == from.key }
        val toIndex = list.indexOfFirst { it.mediaId == to.key }
        if (fromIndex >= 0 && toIndex >= 0) {
            tracks = list.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
            movedDuringDrag = true
            haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("", onBack = onBack) {
            IconButton(onClick = { searching = !searching; if (!searching) query = "" }, shapes = IconButtonDefaults.shapes()) {
                Icon(Icons.Default.Search, "Поиск")
            }
            IconButton(onClick = { reload++ }, enabled = !loading, shapes = IconButtonDefaults.shapes()) {
                Icon(Icons.Default.Refresh, "Обновить")
            }
            Box {
                IconButton(onClick = { menu = true }, shapes = IconButtonDefaults.shapes()) {
                    Icon(Icons.Default.MoreVert, "Меню")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Сбросить свой порядок") },
                        enabled = Library.hasCustomOrder(channel.id),
                        onClick = {
                            menu = false
                            Library.resetOrder(channel.id)
                            tracks = Library.cachedTracks(channel.id)
                        },
                    )
                }
            }
        }

        when {
            loading && all.isEmpty() -> LoadingScreen("Собираю треки…")
            error != null && all.isEmpty() -> MessageScreen("Ошибка: $error")
            all.isEmpty() && !loading -> MessageScreen("Здесь нет аудио")
            else -> LazyColumn(state = listState, modifier = Modifier.weight(1f), contentPadding = PaddingValues(bottom = 12.dp)) {
                item(key = "header") {
                    PlaylistHeader(channel, shown, loading)
                }
                if (searching) {
                    item(key = "search") {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text("Название или исполнитель") },
                            singleLine = true,
                            shape = RoundedCornerShape(28.dp),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
                items(shown, key = { it.mediaId }) { t ->
                    ReorderableItem(reorderState, key = t.mediaId, enabled = canReorder) { isDragging ->
                        val elevation by animateDpAsState(if (isDragging) 10.dp else 0.dp, label = "drag")
                        val scale by animateFloatAsState(if (isDragging) 1.03f else 1f, spring(dampingRatio = 0.5f), label = "dragScale")
                        Surface(
                            shadowElevation = elevation,
                            shape = RoundedCornerShape(20.dp),
                            color = if (isDragging) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
                            modifier = Modifier.padding(horizontal = 8.dp).scale(scale),
                        ) {
                            Box {
                                TrackRow(
                                    track = t,
                                    isCurrent = t.mediaId == player.mediaId,
                                    isPlaying = player.isPlaying,
                                    // Long press: drag to move. Long press and let go without moving: menu.
                                    modifier = if (canReorder) Modifier.longPressDraggableHandle(
                                        onDragStarted = {
                                            movedDuringDrag = false
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        },
                                        onDragStopped = {
                                            if (movedDuringDrag) tracks?.let { Library.saveOrder(channel.id, it) }
                                            else menuFor = t.mediaId
                                        },
                                    ) else Modifier,
                                    onLongClick = if (canReorder) null else {
                                        {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            menuFor = t.mediaId
                                        }
                                    },
                                ) {
                                    val index = shown.indexOf(t)
                                    PlayerConnection.playQueue(shown, index, shuffle = player.shuffle)
                                }
                                TrackMenu(
                                    track = t,
                                    expanded = menuFor == t.mediaId,
                                    isCurrent = t.mediaId == player.mediaId,
                                    onDismiss = { menuFor = null },
                                    onDelete = { menuFor = null; deleting = t },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Telegram-style menu on long press. */
@Composable
private fun TrackMenu(
    track: Track,
    expanded: Boolean,
    isCurrent: Boolean,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        offset = DpOffset(72.dp, 0.dp),
    ) {
        DropdownMenuItem(
            text = { Text("Играть следующим") },
            leadingIcon = { Icon(AppIcons.Next, null) },
            onClick = { onDismiss(); PlayerConnection.playNext(track) },
        )
        if (isCurrent) {
            DropdownMenuItem(
                text = { Text("Повторить один раз") },
                leadingIcon = { Icon(AppIcons.RepeatOne, null) },
                onClick = { onDismiss(); PlayerConnection.repeatCurrentOnce() },
            )
        }
        if (PlayerConnection.isInQueue(track.mediaId)) {
            DropdownMenuItem(
                text = { Text("Убрать из очереди") },
                leadingIcon = { Icon(AppIcons.RemoveQueue, null) },
                onClick = { onDismiss(); PlayerConnection.removeFromQueue(track.mediaId) },
            )
        }
        DropdownMenuItem(
            text = { Text("Удалить из чата", color = MaterialTheme.colorScheme.error) },
            leadingIcon = { Icon(AppIcons.Delete, null, tint = MaterialTheme.colorScheme.error) },
            onClick = onDelete,
        )
    }
}

/** Track change: slides the way you skipped (next = from the right, back = from the left). */
private fun <S> AnimatedContentTransitionScope<S>.trackChange(): ContentTransform {
    val dir = PlayerConnection.direction
    return (slideInHorizontally(spring(dampingRatio = 0.8f, stiffness = 300f)) { it * dir / 2 } +
        fadeIn() + scaleIn(initialScale = 0.9f)) togetherWith
        (slideOutHorizontally(spring(dampingRatio = 0.8f, stiffness = 300f)) { -it * dir / 2 } +
            fadeOut() + scaleOut(targetScale = 0.9f))
}

@Composable
private fun PlaylistHeader(channel: Channel, shown: List<Track>, loading: Boolean) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        ChatAvatar(channel, Modifier.size(168.dp), MaterialShapes.Cookie12Sided.toShape())
        Spacer(Modifier.height(16.dp))
        Text(
            channel.title,
            style = MaterialTheme.typography.headlineMediumEmphasized,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "${shown.size} ${tracksWord(shown.size)} · зажми трек, чтобы перетащить",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
        )
        if (loading) {
            Spacer(Modifier.height(8.dp))
            LinearWavyProgressIndicator(Modifier.fillMaxWidth(0.5f))
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { PlayerConnection.playQueue(shown, 0) },
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.weight(1f).height(56.dp),
            ) {
                Icon(AppIcons.Play, null)
                Spacer(Modifier.width(6.dp))
                Text("Слушать", style = MaterialTheme.typography.titleMediumEmphasized, maxLines = 1)
            }
            Button(
                onClick = { if (shown.isNotEmpty()) PlayerConnection.playQueue(shown, shown.indices.random(), shuffle = true) },
                shapes = ButtonDefaults.shapes(),
                colors = ButtonDefaults.filledTonalButtonColors(),
                modifier = Modifier.weight(1f).height(56.dp),
            ) {
                Icon(AppIcons.Shuffle, null)
                Spacer(Modifier.width(6.dp))
                Text("Случайно", style = MaterialTheme.typography.titleMediumEmphasized, maxLines = 1)
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun TrackRow(
    track: Track,
    isCurrent: Boolean,
    isPlaying: Boolean,
    modifier: Modifier,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val coverShape = if (isCurrent) MaterialShapes.Cookie9Sided.toShape() else RoundedCornerShape(14.dp)
        Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
            TdImage(track.coverSmall, track.mini, Modifier.fillMaxSize().clip(coverShape)) {
                LetterCover(track.title, Modifier.fillMaxSize(), small = true)
            }
            if (isCurrent) {
                Box(
                    Modifier.fillMaxSize().clip(coverShape).background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(if (isPlaying) AppIcons.Pause else AppIcons.Play, null, tint = Color.White)
                }
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                style = if (isCurrent) MaterialTheme.typography.bodyLargeEmphasized else MaterialTheme.typography.bodyLarge,
            )
            Text(
                track.artist,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val downloaded by Downloads.done.collectAsState()
        LaunchedEffect(track.fileId) { Downloads.check(track.fileId) }
        if (track.fileId in downloaded) {
            Icon(
                AppIcons.Saved, "Скачан",
                Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(8.dp))
        }
        if (track.duration > 0) {
            Text(
                formatTime(track.duration * 1000L),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---------- Player ----------

@Composable
internal fun rememberPosition(): State<Long> {
    val pos = remember { mutableLongStateOf(PlayerConnection.positionMs) }
    LaunchedEffect(Unit) {
        // Every frame: the controller estimates the position between updates, so it moves smoothly.
        while (true) {
            withFrameMillis { pos.longValue = PlayerConnection.positionMs }
        }
    }
    return pos
}

@Composable
private fun MiniPlayer(onOpen: () -> Unit) {
    val s by PlayerConnection.state.collectAsState()
    if (!s.hasMedia) return
    val pos by rememberPosition()
    val progress = if (s.durationMs > 0) (pos.toFloat() / s.durationMs).coerceIn(0f, 1f) else 0f

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 6.dp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Column(Modifier.clickable(onClick = onOpen).padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AnimatedContent(
                    targetState = s,
                    contentKey = { it.mediaId },
                    transitionSpec = { trackChange() },
                    modifier = Modifier.weight(1f),
                    label = "miniTrack",
                ) { st ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TdImage(st.coverSmall, null, Modifier.size(48.dp).clip(RoundedCornerShape(14.dp))) {
                            LetterCover(st.title, Modifier.fillMaxSize(), small = true)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(st.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLargeEmphasized)
                            Text(
                                s.error ?: st.artist,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (s.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                PlayPauseButton(s.isPlaying, s.isBuffering, size = 48)
                IconButton(onClick = PlayerConnection::next, shapes = IconButtonDefaults.shapes()) {
                    Icon(AppIcons.Next, "Дальше")
                }
            }
            Spacer(Modifier.height(6.dp))
            LinearWavyProgressIndicator(
                progress = { progress },
                amplitude = { if (s.isPlaying) 1f else 0f },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            )
        }
    }
}

/** Play button that morphs: circle when paused, rounded square when playing. */
@Composable
private fun PlayPauseButton(isPlaying: Boolean, isBuffering: Boolean, size: Int) {
    val corner by animateDpAsState(
        if (isPlaying) (size * 0.3f).dp else (size / 2f).dp,
        spring(dampingRatio = 0.45f, stiffness = 400f),
        label = "playCorner",
    )
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape(corner))
            .background(MaterialTheme.colorScheme.primary)
            .clickable { PlayerConnection.togglePlay() },
        contentAlignment = Alignment.Center,
    ) {
        if (isBuffering && !isPlaying) {
            LoadingIndicator(Modifier.size((size * 0.7f).dp), color = MaterialTheme.colorScheme.onPrimary)
        } else {
            Icon(
                if (isPlaying) AppIcons.Pause else AppIcons.Play,
                if (isPlaying) "Пауза" else "Играть",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size((size * 0.45f).dp),
            )
        }
    }
}

@Composable
private fun NowPlayingScreen(onClose: () -> Unit, onLyrics: () -> Unit) {
    val s by PlayerConnection.state.collectAsState()
    val pos by rememberPosition()
    var dragging by remember { mutableStateOf<Float?>(null) }

    LaunchedEffect(s.hasMedia) { if (!s.hasMedia) onClose() }

    val coverScale by animateFloatAsState(
        if (s.isPlaying) 1f else 0.86f,
        spring(dampingRatio = 0.5f, stiffness = 300f),
        label = "coverScale",
    )
    val coverCorner by animateDpAsState(if (s.isPlaying) 36.dp else 64.dp, spring(dampingRatio = 0.6f), label = "coverCorner")

    val live by Settings.liveBackground.collectAsState()
    val palette = rememberCoverPalette(
        s.coverLarge ?: s.coverSmall,
        listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary),
    )
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                listOf(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f), MaterialTheme.colorScheme.background)
            )
        )
    ) {
        if (live) {
            LiveBackground(
                colors = palette,
                base = MaterialTheme.colorScheme.background,
                intensity = 0.85f,
                playing = s.isPlaying,
            )
        }
        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose, shapes = IconButtonDefaults.shapes()) { Icon(AppIcons.Down, "Свернуть") }
                Text(
                    "Сейчас играет",
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelLargeEmphasized,
                )
                IconButton(onClick = onLyrics, shapes = IconButtonDefaults.shapes()) {
                    Icon(AppIcons.Lyrics, "Текст песни")
                }
            }
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier.weight(1f, fill = false)
                    .aspectRatio(1f, matchHeightConstraintsFirst = true)
                    .align(Alignment.CenterHorizontally)
                    .scale(coverScale),
            ) {
                AnimatedContent(
                    targetState = s,
                    contentKey = { it.mediaId },
                    transitionSpec = { trackChange() },
                    modifier = Modifier.fillMaxSize(),
                    label = "cover",
                ) { st ->
                    Surface(shape = RoundedCornerShape(coverCorner), shadowElevation = 16.dp, modifier = Modifier.fillMaxSize()) {
                        TdImage(st.coverLarge, null, Modifier.fillMaxSize(), maxPx = 1080, fallbackId = st.coverSmall) {
                            LetterCover(st.title, Modifier.fillMaxSize())
                        }
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
            AnimatedContent(
                targetState = s,
                contentKey = { it.mediaId },
                transitionSpec = { trackChange() },
                label = "title",
            ) { st ->
                Column(Modifier.fillMaxWidth()) {
                    Text(st.title, style = MaterialTheme.typography.headlineSmallEmphasized, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        st.artist,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            s.error?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(16.dp))

            val duration = s.durationMs.coerceAtLeast(1)
            val value = dragging ?: (pos.toFloat() / duration).coerceIn(0f, 1f)
            WavySeekBar(
                value = value,
                playing = s.isPlaying,
                enabled = s.durationMs > 0,
                onDrag = { dragging = it },
                onDragEnd = {
                    dragging?.let { PlayerConnection.seekTo((it * duration).toLong()) }
                    dragging = null
                },
            )
            Row(Modifier.fillMaxWidth()) {
                val shownPos = dragging?.let { (it * duration).toLong() } ?: pos
                Text(formatTime(shownPos), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Text(formatTime(s.durationMs), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(16.dp))
            // Glass button group: prev / PLAY / next. They light up with the bass.
            val beat by rememberBeat(s.isPlaying)
            val dark = isAppInDarkTheme()
            val ink = if (dark) Color.White else Color.Black
            val glow = palette.firstOrNull() ?: MaterialTheme.colorScheme.primary
            val playCorner by animateDpAsState(if (s.isPlaying) 24.dp else 40.dp, spring(dampingRatio = 0.45f, stiffness = 500f), label = "glassPlay")
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassButton(
                    onClick = PlayerConnection::previous,
                    beat = beat, glow = glow, dark = dark,
                    start = 36.dp, end = 16.dp,
                    modifier = Modifier.weight(1f).height(80.dp),
                ) { Icon(AppIcons.Previous, "Назад", Modifier.size(32.dp), tint = ink) }
                GlassButton(
                    onClick = { PlayerConnection.togglePlay() },
                    beat = beat, glow = glow, dark = dark, strong = true,
                    start = playCorner, end = playCorner,
                    modifier = Modifier.weight(1.5f).height(80.dp),
                ) {
                    if (s.isBuffering && !s.isPlaying) {
                        LoadingIndicator(Modifier.size(48.dp), color = ink)
                    } else {
                        Icon(
                            if (s.isPlaying) AppIcons.Pause else AppIcons.Play,
                            if (s.isPlaying) "Пауза" else "Играть",
                            Modifier.size(40.dp),
                            tint = ink,
                        )
                    }
                }
                GlassButton(
                    onClick = PlayerConnection::next,
                    beat = beat, glow = glow, dark = dark,
                    start = 16.dp, end = 36.dp,
                    modifier = Modifier.weight(1f).height(80.dp),
                ) { Icon(AppIcons.Next, "Дальше", Modifier.size(32.dp), tint = ink) }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                ToggleIcon(AppIcons.Shuffle, "Перемешать", s.shuffle, PlayerConnection::toggleShuffle)
                ToggleIcon(
                    if (s.repeatMode == Player.REPEAT_MODE_ONE) AppIcons.RepeatOne else AppIcons.Repeat,
                    "Повтор",
                    s.repeatMode != Player.REPEAT_MODE_OFF,
                    PlayerConnection::cycleRepeat,
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

/** Wide play button: pill when paused, squarer when playing, squishes when pressed. */
@Composable
private fun BigPlayButton(isPlaying: Boolean, isBuffering: Boolean, modifier: Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val corner by animateDpAsState(
        when {
            pressed -> 14.dp
            isPlaying -> 24.dp
            else -> 40.dp
        },
        spring(dampingRatio = 0.45f, stiffness = 500f),
        label = "bigPlayCorner",
    )
    Box(
        modifier.clip(RoundedCornerShape(corner))
            .background(MaterialTheme.colorScheme.primary)
            .clickable(interactionSource = interaction, indication = ripple()) { PlayerConnection.togglePlay() },
        contentAlignment = Alignment.Center,
    ) {
        if (isBuffering && !isPlaying) {
            LoadingIndicator(Modifier.size(48.dp), color = MaterialTheme.colorScheme.onPrimary)
        } else {
            Icon(
                if (isPlaying) AppIcons.Pause else AppIcons.Play,
                if (isPlaying) "Пауза" else "Играть",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(40.dp),
            )
        }
    }
}

/** Wavy progress line with a thumb exactly at the playback position; tap or drag to seek. */
@Composable
private fun WavySeekBar(
    value: Float,
    playing: Boolean,
    enabled: Boolean,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    val thumbColor = MaterialTheme.colorScheme.primary
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(44.dp)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset ->
                    onDrag((offset.x / size.width).coerceIn(0f, 1f))
                    onDragEnd()
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { offset -> onDrag((offset.x / size.width).coerceIn(0f, 1f)) },
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragEnd,
                ) { change, _ ->
                    change.consume()
                    onDrag((change.position.x / size.width).coerceIn(0f, 1f))
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val thumbWidth = 4.dp
        LinearWavyProgressIndicator(
            progress = { value },
            amplitude = { if (playing) 1f else 0f },
            modifier = Modifier.fillMaxWidth(),
        )
        Box(
            Modifier.offset(x = (maxWidth * value - thumbWidth / 2).coerceIn(0.dp, maxWidth - thumbWidth))
                .size(thumbWidth, 28.dp)
                .clip(CircleShape)
                .background(thumbColor)
        )
    }
}

@Composable
private fun ToggleIcon(icon: ImageVector, description: String, active: Boolean, onClick: () -> Unit) {
    if (active) {
        FilledIconButton(onClick = onClick, shapes = IconButtonDefaults.shapes()) { Icon(icon, description) }
    } else {
        IconButton(onClick = onClick, shapes = IconButtonDefaults.shapes()) {
            Icon(icon, description, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---------- Shared bits ----------

@Composable
private fun TopBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
            }
        } else {
            Spacer(Modifier.width(12.dp))
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLargeEmphasized,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 4.dp),
        )
        actions()
    }
}

/** Placeholder when there's no picture: a color from the name and the first letter. */
@Composable
internal fun LetterCover(seed: String, modifier: Modifier, small: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    val palette = listOf(
        scheme.primaryContainer to scheme.onPrimaryContainer,
        scheme.secondaryContainer to scheme.onSecondaryContainer,
        scheme.tertiaryContainer to scheme.onTertiaryContainer,
    )
    val (bg, fg) = palette[Math.floorMod(seed.hashCode(), palette.size)]
    Box(modifier.background(bg), contentAlignment = Alignment.Center) {
        if (small) {
            Icon(AppIcons.Note, null, tint = fg)
        } else {
            Text(
                seed.trim().firstOrNull()?.uppercase() ?: "♪",
                color = fg,
                style = MaterialTheme.typography.displayMediumEmphasized,
            )
        }
    }
}

private fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}

private fun tracksWord(n: Int) = plural(n, "трек", "трека", "треков")
private fun playlistsWord(n: Int) = plural(n, "плейлист", "плейлиста", "плейлистов")

private fun plural(n: Int, one: String, few: String, many: String): String {
    val mod100 = n % 100
    val mod10 = n % 10
    return when {
        mod100 in 11..14 -> many
        mod10 == 1 -> one
        mod10 in 2..4 -> few
        else -> many
    }
}
