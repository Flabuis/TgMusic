package com.flabuis.tgmusic.player

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Bass / loudness of what you HEAR right now (0..1), for the live background.
 *
 * Audio is analysed when it's handed to the speaker, which is ahead of what's actually
 * audible (speaker buffer, Bluetooth delay). So each measurement is stored with its
 * timestamp and published only when the sink reports that moment is playing.
 */
@UnstableApi
object BassMeter {
    @Volatile var bass = 0f
        private set
    @Volatile var energy = 0f
        private set
    /** Slow average: calm songs ~0.2, loud/fast ones ~0.8. */
    @Volatile var mood = 0.4f
        private set

    fun reset() {
        bass = 0f
        energy = 0f
    }

    internal fun publish(b: Float, e: Float) {
        bass = b
        energy = e
        mood += (e * (0.5f + 0.5f * sqrt(b)) - mood) * 0.004f
    }
}

/** Wraps the real audio sink: measures each buffer and publishes it in sync with playback. */
@UnstableApi
class MeteringAudioSink(sink: AudioSink) : ForwardingAudioSink(sink) {
    private class Level(val timeUs: Long, val bass: Float, val energy: Float)

    private val queue = ArrayDeque<Level>()
    private var sampleRate = 44100
    private var channels = 2
    private var encoding = C.ENCODING_PCM_16BIT
    private var alpha = 0f
    private var lowPass = 0f
    private var bassPeak = 0.05f
    private var energyPeak = 0.05f
    private var lastAnalyzedUs = Long.MIN_VALUE

    override fun configure(inputFormat: Format, specifiedBufferSize: Int, outputChannels: IntArray?) {
        sampleRate = inputFormat.sampleRate.takeIf { it > 0 } ?: 44100
        channels = inputFormat.channelCount.takeIf { it > 0 } ?: 2
        encoding = inputFormat.pcmEncoding
        alpha = (1 - exp(-2.0 * Math.PI * 150.0 / sampleRate)).toFloat() // low-pass ~150 Hz
        super.configure(inputFormat, specifiedBufferSize, outputChannels)
    }

    override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean {
        // The same buffer can be offered several times until the sink takes it all: measure once.
        if (presentationTimeUs != lastAnalyzedUs) {
            lastAnalyzedUs = presentationTimeUs
            analyze(buffer.duplicate().order(ByteOrder.nativeOrder()), presentationTimeUs)
        }
        return super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
    }

    override fun getCurrentPositionUs(sourceEnded: Boolean): Long {
        val now = super.getCurrentPositionUs(sourceEnded)
        if (now != AudioSink.CURRENT_POSITION_NOT_SET) {
            var latest: Level? = null
            synchronized(queue) {
                while (queue.isNotEmpty() && queue.first().timeUs <= now) latest = queue.removeFirst()
            }
            latest?.let { BassMeter.publish(it.bass, it.energy) }
        }
        return now
    }

    override fun flush() {
        synchronized(queue) { queue.clear() }
        lastAnalyzedUs = Long.MIN_VALUE
        lowPass = 0f
        BassMeter.reset()
        super.flush()
    }

    override fun reset() {
        synchronized(queue) { queue.clear() }
        super.reset()
    }

    private fun analyze(buf: ByteBuffer, timeUs: Long) {
        val bytesPerSample = when (encoding) {
            C.ENCODING_PCM_16BIT -> 2
            C.ENCODING_PCM_FLOAT -> 4
            else -> return
        }
        val frameBytes = bytesPerSample * channels
        // Split into ~20 ms chunks so fast kicks don't get averaged away.
        val chunkFrames = (sampleRate / 50).coerceAtLeast(1)
        var frameIndex = 0L
        while (buf.remaining() >= frameBytes) {
            var bassSum = 0.0
            var allSum = 0.0
            var n = 0
            while (n < chunkFrames && buf.remaining() >= frameBytes) {
                var mono = 0f
                repeat(channels) {
                    mono += if (bytesPerSample == 2) buf.short / 32768f else buf.float
                }
                mono /= channels
                lowPass += alpha * (mono - lowPass)
                bassSum += lowPass * lowPass
                allSum += mono * mono
                n++
            }
            if (n == 0) break
            val bassRms = sqrt(bassSum / n).toFloat()
            val allRms = sqrt(allSum / n).toFloat()
            // Auto gain against a slowly falling peak, so quiet and loud songs both move.
            bassPeak = max(bassRms, bassPeak * 0.997f).coerceAtLeast(0.01f)
            energyPeak = max(allRms, energyPeak * 0.998f).coerceAtLeast(0.01f)
            val b = (bassRms / bassPeak).coerceIn(0f, 1f)
            val level = Level(
                timeUs = timeUs + frameIndex * 1_000_000L / sampleRate,
                bass = b * b,
                energy = (allRms / energyPeak).coerceIn(0f, 1f),
            )
            synchronized(queue) {
                queue.addLast(level)
                while (queue.size > 1000) queue.removeFirst()
            }
            frameIndex += n
        }
    }
}
