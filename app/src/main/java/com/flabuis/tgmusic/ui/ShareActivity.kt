@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.flabuis.tgmusic.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flabuis.tgmusic.data.Channel
import com.flabuis.tgmusic.data.Library
import com.flabuis.tgmusic.td.Td
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.drinkless.tdlib.TdApi
import java.io.File
import java.io.FileOutputStream

/** "Share" → TgMusic: pick a playlist chat and the music files get posted there. */
class ShareActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val uris = sharedUris(intent)
        setContent {
            TgMusicTheme {
                ShareSheet(uris = uris, onClose = { finish() })
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun sharedUris(intent: Intent): List<Uri> = when (intent.action) {
        Intent.ACTION_SEND -> listOfNotNull(
            if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            else intent.getParcelableExtra(Intent.EXTRA_STREAM)
        )
        Intent.ACTION_SEND_MULTIPLE -> (
            if (Build.VERSION.SDK_INT >= 33) intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
            else intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
        ).orEmpty()
        else -> emptyList()
    }
}

private sealed interface ShareStep {
    data object Pick : ShareStep
    data class Sending(val done: Int, val total: Int, val current: String) : ShareStep
    data class Done(val sent: Int, val failed: Int, val chat: String) : ShareStep
}

@Composable
private fun ShareSheet(uris: List<Uri>, onClose: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val auth by Td.authState.collectAsState()
    var chats by remember { mutableStateOf<List<Channel>?>(null) }
    var step by remember { mutableStateOf<ShareStep>(ShareStep.Pick) }
    val scope = rememberCoroutineScope()
    val ready = auth is TdApi.AuthorizationStateReady

    LaunchedEffect(ready) {
        if (ready) chats = try {
            // Chats already used as playlists go first.
            val selected = Library.selected.value.map { it.id }.toSet()
            Library.loadMyChannels().sortedByDescending { it.id in selected }
        } catch (e: Exception) {
            emptyList()
        }
    }

    ModalBottomSheet(onDismissRequest = onClose, sheetState = sheet) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
            when (val st = step) {
                ShareStep.Pick -> {
                    Text(
                        if (uris.size == 1) "Куда отправить трек?" else "Куда отправить ${uris.size} треков?",
                        style = MaterialTheme.typography.titleLargeEmphasized,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                    val list = chats
                    when {
                        uris.isEmpty() -> Hint("Не вижу файлов. Поделись именно аудиофайлом.")
                        !ready -> Hint("Сначала войди в Telegram в самом TgMusic.")
                        list == null -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            LoadingIndicator(Modifier.size(56.dp))
                        }
                        list.isEmpty() -> Hint("Нет групп или каналов, где ты админ.")
                        else -> LazyColumn(Modifier.heightIn(max = 480.dp)) {
                            items(list, key = { it.id }) { ch ->
                                Row(
                                    Modifier.fillMaxWidth()
                                        .clickable {
                                            scope.launch {
                                                step = sendAll(context, uris, ch) { step = it }
                                                Library.invalidate(ch.id)
                                                delay(1500)
                                                onClose()
                                            }
                                        }
                                        .padding(horizontal = 20.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    ChatAvatar(ch, Modifier.size(48.dp), CircleShape)
                                    Spacer(Modifier.width(14.dp))
                                    Text(
                                        ch.title,
                                        style = MaterialTheme.typography.bodyLargeEmphasized,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
                is ShareStep.Sending -> Column(
                    Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    LoadingIndicator(Modifier.size(64.dp))
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Отправляю ${st.done + 1} из ${st.total}",
                        style = MaterialTheme.typography.titleMediumEmphasized,
                    )
                    Text(
                        st.current,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(16.dp))
                    LinearWavyProgressIndicator(
                        progress = { st.done.toFloat() / st.total },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                is ShareStep.Done -> Column(
                    Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier.size(72.dp).clip(MaterialShapes.Cookie9Sided.toShape())
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(AppIcons.Saved, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onPrimary)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (st.failed == 0) "Готово, ${st.sent} в «${st.chat}»"
                        else "Отправлено ${st.sent}, не вышло ${st.failed}",
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(24.dp),
    )
}

/** Copies each file, reads its tags and cover, posts it as audio and waits until Telegram has it. */
private suspend fun sendAll(
    context: Context,
    uris: List<Uri>,
    chat: Channel,
    onStep: (ShareStep) -> Unit,
): ShareStep = coroutineScope {
    // Listen for "sent / failed" from the very start, so a quick upload isn't missed.
    val results = ConcurrentHashMap<Long, String>()
    val listener = launch(start = CoroutineStart.UNDISPATCHED) {
        Td.sendResults.collect { (id, error) -> results[id] = error ?: "" }
    }
    var sent = 0
    var failed = 0
    val dir = File(context.cacheDir, "share").apply { mkdirs() }
    // Leftovers from earlier shares
    dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 24 * 3600_000L }?.forEach { it.delete() }

    uris.forEachIndexed { index, uri ->
        val name = displayName(context, uri) ?: "track_${System.currentTimeMillis()}.mp3"
        onStep(ShareStep.Sending(index, uris.size, name))
        try {
            val file = withContext(Dispatchers.IO) { copyToCache(context, uri, dir, name) }
            val tags = withContext(Dispatchers.IO) { readTags(file, dir) }
            val (fallbackArtist, fallbackTitle) = splitName(name.substringBeforeLast('.'))
            val content = TdApi.InputMessageAudio(
                TdApi.InputAudio(
                    TdApi.InputFileLocal(file.absolutePath),
                    tags.cover?.let { TdApi.InputThumbnail(TdApi.InputFileLocal(it.path), it.width, it.height) },
                    tags.durationSec,
                    tags.title ?: fallbackTitle,
                    tags.artist ?: fallbackArtist,
                ),
                null,
            )
            val msg = Td.send(TdApi.SendMessage(chat.id, null, null, null, null, content))
            // Wait for the upload to finish (or fail). Big files on slow internet can take a while.
            val result = withTimeoutOrNull(10 * 60_000L) {
                while (msg.id !in results) delay(200)
                results[msg.id]
            }
            if (result == "") sent++ else failed++
        } catch (e: Exception) {
            failed++
        }
    }
    listener.cancel()
    ShareStep.Done(sent, failed, chat.title)
}

private fun displayName(context: Context, uri: Uri): String? = try {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }
} catch (e: Exception) {
    null
} ?: uri.lastPathSegment?.substringAfterLast('/')

private fun copyToCache(context: Context, uri: Uri, dir: File, name: String): File {
    val safe = name.replace(Regex("""[\\/:*?"<>|]"""), "_")
    val out = File(dir, "${System.nanoTime()}_$safe")
    context.contentResolver.openInputStream(uri)?.use { input ->
        FileOutputStream(out).use { input.copyTo(it) }
    } ?: throw IllegalStateException("Не удалось открыть файл")
    return out
}

private class Cover(val path: String, val width: Int, val height: Int)
private class Tags(val title: String?, val artist: String?, val durationSec: Int, val cover: Cover?)

/** Title, artist, length and album art from the file's own tags (ID3 etc.). */
private fun readTags(file: File, dir: File): Tags {
    val r = MediaMetadataRetriever()
    return try {
        r.setDataSource(file.absolutePath)
        val title = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }
        val artist = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() }
        val duration = (r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) / 1000
        val cover = r.embeddedPicture?.let { bytes ->
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { bmp ->
                // Telegram wants a small JPEG thumbnail (max 320 px).
                val scale = minOf(1f, 320f / maxOf(bmp.width, bmp.height))
                val small = Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt().coerceAtLeast(1), (bmp.height * scale).toInt().coerceAtLeast(1), true)
                val f = File(dir, "${file.name}.jpg")
                FileOutputStream(f).use { small.compress(Bitmap.CompressFormat.JPEG, 88, it) }
                Cover(f.absolutePath, small.width, small.height)
            }
        }
        Tags(title, artist, duration.toInt(), cover)
    } catch (e: Exception) {
        Tags(null, null, 0, null)
    } finally {
        try { r.release() } catch (_: Exception) {}
    }
}

/** "Artist - Title" → (Artist, Title) */
private fun splitName(name: String): Pair<String, String> {
    val i = name.indexOf(" - ")
    return if (i > 0) name.substring(0, i).trim() to name.substring(i + 3).trim() else "" to name
}
