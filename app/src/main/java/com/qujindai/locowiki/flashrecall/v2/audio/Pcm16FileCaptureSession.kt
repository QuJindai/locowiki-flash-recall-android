package com.qujindai.locowiki.flashrecall.v2.audio

import java.io.File

/** Bounded file-backed input used only by the debuggable emulator QA path. */
internal class Pcm16FileCaptureSession(
    private val file: File,
    private val minSamples: Int = DEFAULT_MIN_SAMPLES,
    private val maxSamples: Int = DEFAULT_MAX_SAMPLES,
) : SegmentCaptureSession {
    private var started = false

    override fun start(): Boolean {
        val byteCount = file.length()
        started = file.isFile &&
            byteCount >= minSamples.toLong() * BYTES_PER_SAMPLE &&
            byteCount % BYTES_PER_SAMPLE == 0L &&
            byteCount <= maxSamples.toLong() * BYTES_PER_SAMPLE
        return started
    }

    override fun stopAndTakeSamples(): FloatArray {
        if (!started) return FloatArray(0)
        started = false
        val bytes = file.readBytes()
        return FloatArray(bytes.size / BYTES_PER_SAMPLE) { index ->
            val offset = index * BYTES_PER_SAMPLE
            val low = bytes[offset].toInt() and 0xff
            val high = bytes[offset + 1].toInt()
            (((high shl 8) or low).toShort().toInt()) / 32768f
        }
    }

    override fun close() { started = false }

    companion object {
        private const val BYTES_PER_SAMPLE = 2
        private const val DEFAULT_MIN_SAMPLES = 16000 * 3
        private const val DEFAULT_MAX_SAMPLES = 16000 * 12
    }
}
