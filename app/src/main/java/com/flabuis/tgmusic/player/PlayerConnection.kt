package com.flabuis.tgmusic.player

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.flabuis.tgmusic.data.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class PlayerUi(
    val hasMedia: Boolean = false,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val title: String = "",
    val artist: String = "",
    val mediaId: String? = null,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val error: String? = null,
    val coverSmall: Int? = null,
    val coverLarge: Int? = null,
)

/** UI side of the player: talks to [PlaybackService] through a MediaController. */
const val EXTRA_COVER_SMALL = "cover_small"
const val EXTRA_COVER_LARGE = "cover_large"

object PlayerConnection {
    private var controller: MediaController? = null
    private var pending: (() -> Unit)? = null

    private val _state = MutableStateFlow(PlayerUi())
    val state: StateFlow<PlayerUi> = _state

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = update()
        override fun onPlayerError(error: PlaybackException) {
            _state.value = _state.value.copy(error = error.cause?.message ?: error.message)
        }
    }

    fun connect(context: Context) {
        if (controller != null) return
        val app = context.applicationContext
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        future.addListener({
            val c = try { future.get() } catch (e: Exception) { return@addListener }
            controller = c
            c.addListener(listener)
            update()
            pending?.invoke()
            pending = null
        }, ContextCompat.getMainExecutor(app))
    }

    private fun withController(action: (MediaController) -> Unit) {
        val c = controller
        if (c != null) action(c) else pending = { controller?.let(action) }
    }

    private fun update() {
        val c = controller ?: return
        val meta = c.mediaMetadata
        _state.value = PlayerUi(
            hasMedia = c.mediaItemCount > 0,
            isPlaying = c.isPlaying,
            isBuffering = c.playbackState == Player.STATE_BUFFERING,
            title = meta.title?.toString() ?: "",
            artist = meta.artist?.toString() ?: "",
            mediaId = c.currentMediaItem?.mediaId,
            durationMs = c.duration.coerceAtLeast(0),
            shuffle = c.shuffleModeEnabled,
            repeatMode = c.repeatMode,
            error = if (c.playerError != null) _state.value.error else null,
            coverSmall = meta.extras?.getInt(EXTRA_COVER_SMALL)?.takeIf { it != 0 },
            coverLarge = meta.extras?.getInt(EXTRA_COVER_LARGE)?.takeIf { it != 0 },
        )
    }

    val positionMs: Long get() = controller?.currentPosition ?: 0L
    val bufferedMs: Long get() = controller?.bufferedPosition ?: 0L

    fun playQueue(tracks: List<Track>, startIndex: Int, shuffle: Boolean = false) {
        if (tracks.isEmpty()) return
        withController { c ->
            c.shuffleModeEnabled = shuffle
            c.setMediaItems(tracks.map { it.toMediaItem() }, startIndex.coerceIn(0, tracks.lastIndex), 0L)
            c.prepare()
            c.play()
        }
    }

    fun togglePlay() = withController { c ->
        if (c.playbackState == Player.STATE_IDLE) c.prepare()
        if (c.isPlaying) c.pause() else c.play()
    }

    fun next() = withController { it.seekToNextMediaItem() }
    fun previous() = withController { it.seekToPrevious() }
    fun seekTo(ms: Long) = withController { it.seekTo(ms) }
    fun toggleShuffle() = withController { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    fun cycleRepeat() = withController {
        it.repeatMode = when (it.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun stop() = withController {
        it.stop()
        it.clearMediaItems()
    }

    private fun Track.toMediaItem(): MediaItem =
        MediaItem.Builder()
            .setMediaId(mediaId)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setIsPlayable(true)
                    .setIsBrowsable(false)
                    .setExtras(Bundle().apply {
                        putInt(EXTRA_COVER_SMALL, coverSmall ?: 0)
                        putInt(EXTRA_COVER_LARGE, coverLarge ?: coverSmall ?: 0)
                    })
                    .build()
            )
            .build()
}
