package com.flabuis.tgmusic.td

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import org.drinkless.tdlib.TdApi
import java.io.IOException
import java.io.InterruptedIOException
import java.io.RandomAccessFile
import kotlin.math.min

/**
 * Streams a Telegram file to ExoPlayer while TDLib downloads it.
 * URI format: tg://file/<fileId>
 *
 * TDLib writes the file to disk in parts; we ask it to download starting from the
 * position the player needs and read the bytes as soon as they are on disk.
 */
@UnstableApi
class TgDataSource : BaseDataSource(/* isNetwork = */ true) {

    private var uri: Uri? = null
    private var fileId = 0
    private var position = 0L
    private var bytesRemaining = 0L
    private var availableEnd = 0L      // bytes [position, availableEnd) are known to be on disk
    private var raf: RandomAccessFile? = null
    private var rafPath: String? = null
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        fileId = dataSpec.uri.lastPathSegment?.toIntOrNull()
            ?: throw IOException("Bad Telegram URI: ${dataSpec.uri}")
        transferInitializing(dataSpec)

        position = dataSpec.position
        availableEnd = position
        val file = Td.sendBlocking(TdApi.DownloadFile(fileId, PRIORITY, position, 0, false))
        val size = if (file.size > 0) file.size else file.expectedSize
        if (size in 1 until position) throw IOException("Position $position is beyond file size $size")

        bytesRemaining = when {
            dataSpec.length != C.LENGTH_UNSET.toLong() -> dataSpec.length
            size > 0 -> size - position
            else -> C.LENGTH_UNSET.toLong()
        }
        opened = true
        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT

        var read: Int
        while (true) {
            if (position >= availableEnd) {
                val path = waitForData() ?: return C.RESULT_END_OF_INPUT
                if (path != rafPath) {
                    raf?.close()
                    raf = RandomAccessFile(path, "r")
                    rafPath = path
                }
            }

            var toRead = min(length.toLong(), availableEnd - position)
            if (bytesRemaining != C.LENGTH_UNSET.toLong()) toRead = min(toRead, bytesRemaining)

            val file = raf ?: throw IOException("File is not open")
            file.seek(position)
            read = file.read(buffer, offset, toRead.toInt())
            if (read > 0) break
            // Reported as downloaded but not readable yet: wait and ask TDLib again.
            availableEnd = position
            try {
                Thread.sleep(POLL_MS)
            } catch (e: InterruptedException) {
                throw InterruptedIOException()
            }
        }
        position += read
        if (bytesRemaining != C.LENGTH_UNSET.toLong()) bytesRemaining -= read
        bytesTransferred(read)
        return read
    }

    /** Blocks until some bytes at [position] are on disk. Returns the local path, or null at end of file. */
    private fun waitForData(): String? {
        var waited = 0L
        while (true) {
            if (Thread.currentThread().isInterrupted) throw InterruptedIOException()
            val file = Td.sendBlocking(TdApi.GetFile(fileId))
            val local = file.local
            if (local.path.isNotEmpty()) {
                val prefix = try {
                    Td.sendBlocking(TdApi.GetFileDownloadedPrefixSize(fileId, position)).size
                } catch (e: TdException) {
                    0L
                }
                if (prefix > 0) {
                    availableEnd = position + prefix
                    return local.path
                }
                if (local.isDownloadingCompleted) return null
            }
            if (!local.isDownloadingActive && !local.isDownloadingCompleted) {
                if (!local.canBeDownloaded) throw IOException("Файл нельзя скачать")
                Td.sendBlocking(TdApi.DownloadFile(fileId, PRIORITY, position, 0, false))
            }
            try {
                Thread.sleep(POLL_MS)
            } catch (e: InterruptedException) {
                throw InterruptedIOException()
            }
            waited += POLL_MS
            if (waited > TIMEOUT_MS) throw IOException("Трек не грузится, проверь интернет")
        }
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        try {
            raf?.close()
        } finally {
            raf = null
            rafPath = null
            uri = null
            if (opened) {
                opened = false
                transferEnded()
                // Keep downloading the rest quietly, so next time the track plays from the phone.
                if (fileId != 0) Td.sendAsync(TdApi.DownloadFile(fileId, 1, 0, 0, false))
            }
        }
    }

    class Factory : DataSource.Factory {
        override fun createDataSource(): DataSource = TgDataSource()
    }

    companion object {
        private const val PRIORITY = 32
        private const val POLL_MS = 40L
        private const val TIMEOUT_MS = 60_000L

        fun uriFor(fileId: Int): Uri = Uri.parse("tg://file/$fileId")
    }
}
