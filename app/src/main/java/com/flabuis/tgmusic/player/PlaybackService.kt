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
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.flabuis.tgmusic.td.TgDataSource
import com.flabuis.tgmusic.ui.ImageCache
import com.flabuis.tgmusic.ui.MainActivity
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/** Background playback: notification, lock screen and headphone buttons come from MediaSession. */
@UnstableApi
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private val scope = MainScope()

    override fun onCreate() {
        super.onCreate()
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
        val player = ExoPlayer.Builder(this, renderers)
            .setMediaSourceFactory(ProgressiveMediaSource.Factory(TgDataSource.Factory()))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true) // pause when headphones are unplugged
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        session = MediaSession.Builder(this, player)
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

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying) BassMeter.reset()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                mediaItem?.let { loadArtwork(player, it) }
            }
        })
    }

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
        session?.run {
            player.release()
            release()
        }
        session = null
        scope.cancel()
        super.onDestroy()
    }
}
