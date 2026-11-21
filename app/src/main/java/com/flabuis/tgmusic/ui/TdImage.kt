package com.flabuis.tgmusic.ui

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.flabuis.tgmusic.td.Td
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.drinkless.tdlib.TdApi

/** Downloads small Telegram images (covers, avatars) and keeps them in memory. */
object ImageCache {
    private val cache = object : LruCache<Int, ImageBitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: Int, value: ImageBitmap) = value.width * value.height * 4
    }

    fun peek(fileId: Int): ImageBitmap? = cache.get(fileId)

    suspend fun load(fileId: Int, maxPx: Int = 720): ImageBitmap? {
        cache.get(fileId)?.let { return it }
        val file = Td.send(TdApi.DownloadFile(fileId, 1, 0, 0, true))
        if (!file.local.isDownloadingCompleted || file.local.path.isEmpty()) return null
        val bmp = withContext(Dispatchers.IO) { decode(file.local.path, maxPx) } ?: return null
        cache.put(fileId, bmp)
        return bmp
    }

    /** Local path of a downloaded image (for the notification artwork). */
    suspend fun path(fileId: Int): String? {
        val file = Td.send(TdApi.DownloadFile(fileId, 8, 0, 0, true))
        return file.local.path.takeIf { file.local.isDownloadingCompleted && it.isNotEmpty() }
    }

    private fun decode(path: String, maxPx: Int): ImageBitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxPx && bounds.outHeight / (sample * 2) >= maxPx) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeFile(path, opts)?.asImageBitmap()
    }
}

/**
 * Shows a Telegram image: the tiny blurred preview first, then the real picture.
 * Falls back to [placeholder] when there is nothing to show.
 */
@Composable
fun TdImage(
    fileId: Int?,
    mini: ByteArray?,
    modifier: Modifier = Modifier,
    maxPx: Int = 720,
    /** Smaller picture to show while [fileId] loads, or if it can't be downloaded. */
    fallbackId: Int? = null,
    placeholder: @Composable () -> Unit,
) {
    var bitmap by remember(fileId, fallbackId) {
        mutableStateOf(fileId?.let { ImageCache.peek(it) } ?: fallbackId?.let { ImageCache.peek(it) })
    }
    val preview = remember(mini) {
        mini?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
    }
    LaunchedEffect(fileId, fallbackId) {
        if (fileId != null && ImageCache.peek(fileId) != null) return@LaunchedEffect
        if (fallbackId != null && fallbackId != fileId && bitmap == null) {
            bitmap = try { ImageCache.load(fallbackId, maxPx) } catch (e: Exception) { null }
        }
        if (fileId != null) {
            val big = try { ImageCache.load(fileId, maxPx) } catch (e: Exception) { null }
            if (big != null) bitmap = big
        }
    }
    Box(modifier) {
        val b = bitmap
        when {
            b != null -> Image(b, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            preview != null -> Image(
                preview, null, Modifier.fillMaxSize().blur(6.dp), contentScale = ContentScale.Crop
            )
            else -> placeholder()
        }
    }
}
