package com.flabuis.tgmusic.player

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Listens to the audio the player is about to play (no microphone, no permissions)
 * and measures how hard the bass hits and how energetic the song is.
 * Values are 0..1, read by the live background every frame.
 */
@UnstableApi
object BassMeter : TeeAudioProcessor.AudioBufferSink {
    /** Low frequencies right now (kick, bass). */
    @Volatile var bass = 0f
        private set
    /** Loudness right now. */
    @Volatile var energy = 0f
        private set
    /** Slow average of energy: calm songs ~0.2, loud/fast ones ~0.8. */
    @Volatile var mood = 0.4f
        private set

    private var sampleRate = 44100
    private var channels = 2
    private var encoding = C.ENCODING_PCM_16BIT
    private var lowPass = 0f
    private var alpha = 0f
    private var bassPeak = 0.05f
    private var energyPeak = 0.05f

    override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
        sampleRate = sampleRateHz.coerceAtLeast(1)
        channels = channelCount.coerceAtLeast(1)
        this.encoding = encoding
        // one-pole low-pass at ~150 Hz
        alpha = (1 - exp(-2.0 * Math.PI * 150.0 / sampleRate)).toFloat()
        lowPass = 0f
    }

    override fun handleBuffer(buffer: ByteBuffer) {
        val buf = buffer.duplicate().order(ByteOrder.nativeOrder())
        var bassSum = 0.0
        var allSum = 0.0
        var frames = 0
        when (encoding) {
            C.ENCODING_PCM_16BIT -> {
                val frameBytes = 2 * channels
                while (buf.remaining() >= frameBytes) {
                    var mono = 0f
                    repeat(channels) { mono += buf.short / 32768f }
                    mono /= channels
                    lowPass += alpha * (mono - lowPass)
                    bassSum += lowPass * lowPass
                    allSum += mono * mono
                    frames++
                }
            }
            C.ENCODING_PCM_FLOAT -> {
                val frameBytes = 4 * channels
                while (buf.remaining() >= frameBytes) {
                    var mono = 0f
                    repeat(channels) { mono += buf.float }
                    mono /= channels
                    lowPass += alpha * (mono - lowPass)
                    bassSum += lowPass * lowPass
                    allSum += mono * mono
                    frames++
                }
            }
            else -> return
        }
        if (frames == 0) return
        val bassRms = sqrt(bassSum / frames).toFloat()
        val allRms = sqrt(allSum / frames).toFloat()

        // Auto gain: compare against a slowly falling peak so quiet and loud songs both "move".
        bassPeak = max(bassRms, bassPeak * 0.995f).coerceAtLeast(0.01f)
        energyPeak = max(allRms, energyPeak * 0.997f).coerceAtLeast(0.01f)
        val b = (bassRms / bassPeak).coerceIn(0f, 1f)
        bass = b * b // punchier: small values stay low, kicks jump
        energy = (allRms / energyPeak).coerceIn(0f, 1f)
        mood += (energy * (0.5f + 0.5f * b) - mood) * 0.002f
    }

    fun reset() {
        bass = 0f
        energy = 0f
    }
}
