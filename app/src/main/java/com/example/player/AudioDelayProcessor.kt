package com.example.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.min

/**
 * Applies a fixed initial audio offset to decoded PCM:
 * positive values insert silence (audio later), negative values trim samples (audio earlier).
 * Encoded passthrough formats are intentionally left untouched by Media3.
 */
@androidx.media3.common.util.UnstableApi
class AudioDelayProcessor(
    private val delayMs: Int
) : BaseAudioProcessor() {
    private var bytesPerFrame = 0
    private var remainingOffsetBytes = 0L

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (delayMs == 0 || inputAudioFormat.encoding != C.ENCODING_PCM_16BIT ||
            inputAudioFormat.sampleRate <= 0 || inputAudioFormat.channelCount <= 0
        ) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        bytesPerFrame = inputAudioFormat.channelCount * 2
        return inputAudioFormat
    }

    override fun onFlush() {
        val frames = (abs(delayMs).toLong() * inputAudioFormat.sampleRate / 1000L)
        remainingOffsetBytes = frames * bytesPerFrame
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val available = inputBuffer.remaining()
        if (available == 0) return

        if (delayMs > 0 && remainingOffsetBytes > 0L) {
            val silenceBytes = min(remainingOffsetBytes, Int.MAX_VALUE.toLong()).toInt()
            val output = replaceOutputBuffer(silenceBytes + available)
            val silence = ByteArray(silenceBytes)
            output.put(silence)
            output.put(inputBuffer)
            remainingOffsetBytes -= silenceBytes.toLong()
            output.flip()
            return
        }

        if (delayMs < 0 && remainingOffsetBytes > 0L) {
            val skipped = min(remainingOffsetBytes, available.toLong()).toInt()
            inputBuffer.position(inputBuffer.position() + skipped)
            remainingOffsetBytes -= skipped.toLong()
        }
        if (inputBuffer.hasRemaining()) {
            val output = replaceOutputBuffer(inputBuffer.remaining())
            output.put(inputBuffer)
            output.flip()
        }
    }

    override fun getDurationAfterProcessorApplied(durationUs: Long): Long {
        return if (delayMs > 0) durationUs + delayMs * 1000L
        else (durationUs + delayMs * 1000L).coerceAtLeast(0L)
    }

    override fun onReset() {
        bytesPerFrame = 0
        remainingOffsetBytes = 0L
    }
}
