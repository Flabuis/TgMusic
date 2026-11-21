package com.flabuis.tgmusic.widget

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.palette.graphics.Palette
import com.flabuis.tgmusic.R
import com.flabuis.tgmusic.player.PlaybackService
import com.flabuis.tgmusic.ui.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

/** What the widget shows; saved so it survives the app being closed. */
object WidgetState {
    private const val PREFS = "widget"

    data class Snapshot(
        val title: String,
        val artist: String,
        val playing: Boolean,
        val coverPath: String?,
        val colors: List<Int>,
    )

    fun load(context: Context): Snapshot {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Snapshot(
            title = p.getString("title", null) ?: "TgMusic",
            artist = p.getString("artist", null) ?: "Ничего не играет",
            playing = p.getBoolean("playing", false),
            coverPath = p.getString("cover", null)?.takeIf { File(it).exists() },
            colors = p.getString("colors", null)?.split(',')?.mapNotNull { it.toIntOrNull() }.orEmpty(),
        )
    }

    /** Called by the player when the track, play state or cover changes. */
    suspend fun update(context: Context, title: String, artist: String, playing: Boolean, coverPath: String?) {
        val old = load(context)
        val colors = if (coverPath != null && coverPath != old.coverPath) paletteOf(coverPath)
                     else if (coverPath == null && title != old.title) emptyList()
                     else old.colors
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("title", title)
            .putString("artist", artist)
            .putBoolean("playing", playing)
            .putString("cover", coverPath)
            .putString("colors", colors.joinToString(","))
            .apply()
        TgWidget().updateAll(context)
    }

    private suspend fun paletteOf(path: String): List<Int> = withContext(Dispatchers.Default) {
        val bmp = decode(path, 200) ?: return@withContext emptyList()
        val p = Palette.from(bmp).maximumColorCount(16).generate()
        listOfNotNull(p.vibrantSwatch, p.darkVibrantSwatch, p.mutedSwatch, p.lightVibrantSwatch, p.dominantSwatch)
            .map { it.rgb }.distinct().take(4)
    }

    fun decode(path: String, maxPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxPx && bounds.outHeight / (sample * 2) >= maxPx) sample *= 2
        return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    /** A still frame of the live background: soft blobs in the cover colors on black. */
    fun background(colors: List<Int>, seed: Int): Bitmap {
        val w = 640
        val h = 320
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(android.graphics.Color.rgb(14, 14, 18))
        val palette = colors.ifEmpty { listOf(0xFF4C2C8F.toInt(), 0xFF1E3A8A.toInt()) }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        palette.forEachIndexed { i, c ->
            val t = seed * 0.37f + i * 1.9f
            val cx = w * (0.5f + 0.4f * sin(t))
            val cy = h * (0.5f + 0.45f * cos(t * 1.3f))
            val r = h * (0.9f + 0.15f * i)
            val color = ColorUtils.setAlphaComponent(ColorUtils.blendARGB(c, android.graphics.Color.BLACK, 0.15f), 170)
            paint.shader = RadialGradient(cx, cy, r, color, android.graphics.Color.TRANSPARENT, Shader.TileMode.CLAMP)
            canvas.drawCircle(cx, cy, r, paint)
        }
        return bmp
    }
}

class TgWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val st = WidgetState.load(context)
        val bg = WidgetState.background(st.colors, st.title.hashCode())
        val cover = st.coverPath?.let { WidgetState.decode(it, 256) }
        provideContent { WidgetContent(st, bg, cover) }
    }
}

@androidx.compose.runtime.Composable
private fun WidgetContent(st: WidgetState.Snapshot, bg: Bitmap, cover: Bitmap?) {
    val white = ColorProvider(Color.White)
    val dim = ColorProvider(Color.White.copy(alpha = 0.7f))
    Box(
        GlanceModifier.fillMaxSize().cornerRadius(28.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        Image(ImageProvider(bg), null, GlanceModifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Row(
            GlanceModifier.fillMaxSize().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(GlanceModifier.size(84.dp).cornerRadius(20.dp)) {
                if (cover != null) {
                    Image(ImageProvider(cover), null, GlanceModifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Box(
                        GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.w_glass)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(ImageProvider(R.drawable.ic_w_note), null, GlanceModifier.size(36.dp))
                    }
                }
            }
            Spacer(GlanceModifier.width(14.dp))
            Column(GlanceModifier.defaultWeight()) {
                Text(st.title, maxLines = 1, style = TextStyle(color = white, fontSize = 17.sp, fontWeight = FontWeight.Bold))
                Text(st.artist, maxLines = 1, style = TextStyle(color = dim, fontSize = 13.sp))
                Spacer(GlanceModifier.height(10.dp))
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    WidgetButton(R.drawable.ic_w_prev, R.drawable.w_glass, actionRunCallback<PrevAction>(), 44)
                    Spacer(GlanceModifier.width(8.dp))
                    WidgetButton(
                        if (st.playing) R.drawable.ic_w_pause else R.drawable.ic_w_play,
                        R.drawable.w_glass_strong, actionRunCallback<PlayPauseAction>(), 56,
                    )
                    Spacer(GlanceModifier.width(8.dp))
                    WidgetButton(R.drawable.ic_w_next, R.drawable.w_glass, actionRunCallback<NextAction>(), 44)
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun WidgetButton(icon: Int, bg: Int, action: androidx.glance.action.Action, width: Int) {
    Box(
        GlanceModifier.width(width.dp).height(44.dp).background(ImageProvider(bg)).clickable(action),
        contentAlignment = Alignment.Center,
    ) {
        Image(ImageProvider(icon), null, GlanceModifier.size(22.dp))
    }
}

/** Sends a command to the player service from the widget. */
private suspend fun withPlayer(context: Context, block: (MediaController) -> Unit) = withContext(Dispatchers.Main) {
    val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
    val controller = MediaController.Builder(context, token).buildAsync().await()
    try {
        block(controller)
        delay(300)
    } finally {
        controller.release()
    }
}

class PlayPauseAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) =
        withPlayer(context) { c ->
            if (c.isPlaying) c.pause() else { c.prepare(); c.play() }
        }
}

class NextAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) =
        withPlayer(context) { it.seekToNextMediaItem() }
}

class PrevAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) =
        withPlayer(context) { it.seekToPrevious() }
}

class TgWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TgWidget()
}
