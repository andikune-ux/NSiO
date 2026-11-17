package com.example.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * Ten-band peaking equalizer for decoded PCM.
 * Coefficients are updated atomically so live slider changes do not restart playback.
 * Encoded passthrough audio is intentionally untouched by Media3 processors.
 */
@androidx.media3.common.util.UnstableApi
class TenBandEqualizerProcessor(initialGainsDb: FloatArray = FloatArray(10)) : BaseAudioProcessor() {
    companion object {
        val CENTER_FREQUENCIES_HZ = intArrayOf(31, 62, 125, 250, 500, 1_000, 2_000, 4_000, 8_000, 16_000)
        private const val Q = 1.4
    }

    private data class Coefficients(val b0: Double, val b1: Double, val b2: Double, val a1: Double, val a2: Double)

    @Volatile private var gainsDb = FloatArray(10) { index -> initialGainsDb.getOrElse(index) { 0f }.coerceIn(-12f, 12f) }
    @Volatile private var coefficients = Array(10) { identity() }
    private var sampleRate = 0
    private var channels = 0
    private var z1 = Array(10) { DoubleArray(0) }
    private var z2 = Array(10) { DoubleArray(0) }

    fun setBandGain(index: Int, gainDb: Float) {
        if (index !in 0..9 || !gainDb.isFinite()) return
        val updated = gainsDb.copyOf()
        updated[index] = gainDb.coerceIn(-12f, 12f)
        gainsDb = updated
        updateCoefficients()
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if ((inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT && inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) ||
            inputAudioFormat.sampleRate <= 0 || inputAudioFormat.channelCount <= 0
        ) return AudioProcessor.AudioFormat.NOT_SET
        sampleRate = inputAudioFormat.sampleRate
        channels = inputAudioFormat.channelCount
        clearFilterState()
        updateCoefficients()
        return inputAudioFormat
    }

    private fun updateCoefficients() {
        if (sampleRate <= 0) return
        coefficients = Array(10) { index ->
            val gain = gainsDb[index].toDouble()
            if (kotlin.math.abs(gain) < 0.001) return@Array identity()
            val frequency = CENTER_FREQUENCIES_HZ[index].toDouble().coerceAtMost(sampleRate * 0.45)
            val omega = 2.0 * PI * frequency / sampleRate
            val alpha = sin(omega) / (2.0 * Q)
            val a = 10.0.pow(gain / 40.0)
            val cosOmega = cos(omega)
            val a0 = 1.0 + alpha / a
            Coefficients(
                (1.0 + alpha * a) / a0,
                (-2.0 * cosOmega) / a0,
                (1.0 - alpha * a) / a0,
                (-2.0 * cosOmega) / a0,
                (1.0 - alpha / a) / a0
            )
        }
    }

    private fun clearFilterState() {
        z1 = Array(10) { DoubleArray(channels) }
        z2 = Array(10) { DoubleArray(channels) }
    }

    override fun onFlush() = clearFilterState()

    override fun queueInput(inputBuffer: ByteBuffer) {
        val bytesPerSample = when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_FLOAT -> 4
            C.ENCODING_PCM_16BIT -> 2
            else -> return
        }
        if (channels <= 0) return
        val sampleCount = inputBuffer.remaining() / bytesPerSample
        if (sampleCount <= 0) return
        val output = replaceOutputBuffer(sampleCount * bytesPerSample).order(ByteOrder.LITTLE_ENDIAN)
        val input = inputBuffer.order(ByteOrder.LITTLE_ENDIAN)
        val currentCoefficients = coefficients

        repeat(sampleCount) { sampleIndex ->
            val channel = sampleIndex % channels
            var sample = if (bytesPerSample == 4) input.float.toDouble() else input.short / 32768.0
            for (band in 0 until 10) {
                val c = currentCoefficients[band]
                val filtered = c.b0 * sample + z1[band][channel]
                z1[band][channel] = c.b1 * sample - c.a1 * filtered + z2[band][channel]
                z2[band][channel] = c.b2 * sample - c.a2 * filtered
                sample = filtered
            }
            sample = sample.coerceIn(-1.0, 1.0)
            if (bytesPerSample == 4) output.putFloat(sample.toFloat())
            else output.putShort((sample * 32767.0).toInt().coerceIn(-32768, 32767).toShort())
        }
        inputBuffer.position(inputBuffer.limit())
        output.flip()
    }

    override fun onReset() {
        sampleRate = 0
        channels = 0
        z1 = Array(10) { DoubleArray(0) }
        z2 = Array(10) { DoubleArray(0) }
        coefficients = Array(10) { identity() }
    }

    private fun identity() = Coefficients(1.0, 0.0, 0.0, 0.0, 0.0)
}
