package com.example.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Lightweight dialogue-oriented dynamic range compressor for decoded PCM.
 * It gently reduces peaks while leaving quieter dialogue comparatively prominent.
 * Encoded passthrough audio is not processed by Media3 and remains unchanged.
 */
@androidx.media3.common.util.UnstableApi
class AudioCompressorProcessor(
    private val enabled: Boolean
) : BaseAudioProcessor() {
    private var sampleRate = 0
    private var channels = 0
    private var envelope = FloatArray(0)

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (!enabled ||
            (inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT &&
                inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) ||
            inputAudioFormat.sampleRate <= 0 || inputAudioFormat.channelCount <= 0
        ) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        sampleRate = inputAudioFormat.sampleRate
        channels = inputAudioFormat.channelCount
        envelope = FloatArray(channels)
        return inputAudioFormat
    }

    override fun onFlush() {
        envelope = FloatArray(channels)
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val bytesPerSample = if (inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT) 4 else 2
        val sampleCount = inputBuffer.remaining() / bytesPerSample
        if (sampleCount <= 0 || channels <= 0 || sampleRate <= 0) return

        val output = replaceOutputBuffer(sampleCount * bytesPerSample)
        val data = inputBuffer.order(ByteOrder.LITTLE_ENDIAN)
        val attack = 1f - kotlin.math.exp(-1f / (sampleRate * 0.008f))
        val release = 1f - kotlin.math.exp(-1f / (sampleRate * 0.120f))
        val threshold = 0.32f
        val ratio = 3.0f
        val makeup = 1.12f

        repeat(sampleCount) { index ->
            val channel = index % channels
            val sample = if (bytesPerSample == 4) data.float else data.short / 32768f
            val magnitude = abs(sample).coerceIn(0f, 1f)
            val coefficient = if (magnitude > envelope[channel]) attack else release
            envelope[channel] += (magnitude - envelope[channel]) * coefficient

            val level = max(envelope[channel], 0.00001f)
            val compressedLevel = if (level <= threshold) {
                level
            } else {
                threshold + (level - threshold) / ratio
            }
            val gain = (compressedLevel / level * makeup).coerceIn(0.25f, 1.35f)
            val processed = (sample * gain).coerceIn(-1f, 1f)

            if (bytesPerSample == 4) {
                output.putFloat(processed)
            } else {
                output.putShort((processed * 32767f).toInt().coerceIn(-32768, 32767).toShort())
            }
        }
        inputBuffer.position(inputBuffer.limit())
        output.flip()
    }

    override fun onReset() {
        sampleRate = 0
        channels = 0
        envelope = FloatArray(0)
    }
}
