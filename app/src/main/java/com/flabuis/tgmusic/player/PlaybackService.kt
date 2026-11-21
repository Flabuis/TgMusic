package com.flabuis.tgmusic.player

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.source.ShuffleOrder
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.flabuis.tgmusic.data.Settings
import com.flabuis.tgmusic.td.TgDataSource
import com.flabuis.tgmusic.ui.ImageCache
import com.flabuis.tgmusic.ui.MainActivity
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Background playback: notification, lock screen and headphone buttons come from MediaSession. */
@UnstableApi
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private val scope = MainScope()
    private var fadeJob: Job? = null
    private var fadingOut: ExoPlayer? = null

    private val musicAttributes = AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .build()

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!isPlaying) BassMeter.reset()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val p = session?.player as? ExoPlayer ?: return
            mediaItem?.let { loadArtwork(p, it) }
        }
    }

    /** A player that measures the bass and streams from Telegram. */
    private fun newPlayer(): ExoPlayer {
        // Audio is measured on its way to the speaker and shown in sync with what you hear.
        val renderers = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): AudioSink = MeteringAudioSink(
                DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .build()
            )
        }
        return ExoPlayer.Builder(this, renderers)
            .setMediaSourceFactory(ProgressiveMediaSource.Factory(TgDataSource.Factory()))
            .setAudioAttributes(musicAttributes, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true) // pause when headphones are unplugged
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
            .also { it.addListener(listener) }
    }

    override fun onCreate() {
        super.onCreate()
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        session = MediaSession.Builder(this, newPlayer())
            .setSessionActivity(openApp)
            .setCallback(object : MediaSession.Callback {
                // Items coming from a controller lose their URI, so rebuild it from mediaId.
                override fun onAddMediaItems(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    mediaItems: MutableList<MediaItem>
                ): ListenableFuture<MutableList<MediaItem>> {
                    val resolved = mediaItems.map { item ->
                        val fileId = item.mediaId.substringAfterLast('_').toIntOrNull()
                        if (fileId == null) item
                        else item.buildUpon().setUri(TgDataSource.uriFor(fileId)).build()
                    }.toMutableList()
                    return Futures.immediateFuture(resolved)
                }
            })
            .build()

        watchForCrossfade()
    }

    // ---------- Crossfade ----------

    /** Watches the end of each track and starts the next one early, overlapping. */
    private fun watchForCrossfade() {
        scope.launch {
            while (isActive) {
                delay(100)
                val fadeMs = Settings.crossfadeSec.value * 1000L
                val p = session?.player as? ExoPlayer ?: continue
                if (fadeMs <= 0 || fadeJob?.isActive == true) continue
                if (!p.isPlaying || p.repeatMode == Player.REPEAT_MODE_ONE || !p.hasNextMediaItem()) continue
                val duration = p.duration
                if (duration == C.TIME_UNSET || duration < fadeMs * 3) continue
                val remaining = duration - p.currentPosition
                // fade exactly over what's left, so the old track ends as the fade ends
                if (remaining <= fadeMs) startCrossfade(p, remaining.coerceAtLeast(500))
            }
        }
    }

    /**
     * Two players at once: the new one starts the next track silently and becomes the
     * session player (notification, app UI follow it), the old one fades out underneath.
     */
    private fun startCrossfade(old: ExoPlayer, fadeMs: Long) {
        val next = newPlayer()
        val items = (0 until old.mediaItemCount).map { old.getMediaItemAt(it) }
        next.setMediaItems(items, old.nextMediaItemIndex, 0L)
        next.repeatMode = old.repeatMode
        if (old.shuffleModeEnabled) {
            next.setShuffleOrder(ShuffleOrder.DefaultShuffleOrder(shuffleOrderOf(old), System.nanoTime()))
            next.shuffleModeEnabled = true
        }
        next.volume = 0f
        next.prepare()

        // The old player must only finish its current song and then stop. Without this it
        // moved on to the next song by itself, and you heard that song start a second time.
        old.removeListener(listener)
        val current = old.currentMediaItemIndex
        if (current + 1 < old.mediaItemCount) old.removeMediaItems(current + 1, old.mediaItemCount)
        if (current > 0) old.removeMediaItems(0, current)
        old.shuffleModeEnabled = false
        old.repeatMode = Player.REPEAT_MODE_OFF

        // Only the new player keeps audio focus; the old one just finishes quietly.
        old.setAudioAttributes(musicAttributes, false)
        old.setHandleAudioBecomingNoisy(false)
        next.play()
        PlayerConnection.markForward()
        session?.player = next

        fadingOut = old
        fadeJob = scope.launch {
            val steps = (fadeMs / 40).toInt().coerceAtLeast(1)
            for (i in 1..steps) {
                if (!next.playWhenReady) old.pause() // paused during the fade: stop both
                val t = i.toFloat() / steps
                // equal-power curve: loudness stays even in the middle
                next.volume = sin(t * PI / 2).toFloat()
                old.volume = cos(t * PI / 2).toFloat()
                delay(40)
            }
            next.volume = 1f
            old.release()
            fadingOut = null
        }
    }

    /** Current shuffled play order of [p], so the new player continues the same shuffle. */
    private fun shuffleOrderOf(p: ExoPlayer): IntArray {
        val timeline = p.currentTimeline
        val order = ArrayList<Int>(timeline.windowCount)
        var i = timeline.getFirstWindowIndex(true)
        while (i != C.INDEX_UNSET && order.size < timeline.windowCount) {
            order += i
            i = timeline.getNextWindowIndex(i, Player.REPEAT_MODE_OFF, true)
        }
        return if (order.size == timeline.windowCount) order.toIntArray() else IntArray(timeline.windowCount) { it }
    }

    // ---------- Artwork ----------

    /** Puts the album cover into the notification / lock screen once it's downloaded. */
    private fun loadArtwork(player: ExoPlayer, item: MediaItem) {
        if (item.mediaMetadata.artworkUri != null) return
        val large = item.mediaMetadata.extras?.getInt(EXTRA_COVER_LARGE) ?: 0
        val small = item.mediaMetadata.extras?.getInt(EXTRA_COVER_SMALL) ?: 0
        if (large == 0 && small == 0) return
        scope.launch {
            val path = listOf(large, small).filter { it != 0 }.distinct().firstNotNullOfOrNull { id ->
                try { ImageCache.path(id) } catch (e: Exception) { null }
            } ?: return@launch
            for (i in 0 until player.mediaItemCount) {
                val current = player.getMediaItemAt(i)
                if (current.mediaId == item.mediaId) {
                    player.replaceMediaItem(
                        i,
                        current.buildUpon().setMediaMetadata(
                            current.mediaMetadata.buildUpon().setArtworkUri(Uri.fromFile(File(path))).build()
                        ).build()
                    )
                    break
                }
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        fadingOut?.release()
        fadingOut = null
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}
