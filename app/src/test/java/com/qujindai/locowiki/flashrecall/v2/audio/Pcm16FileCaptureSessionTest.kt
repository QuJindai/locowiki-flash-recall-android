package com.qujindai.locowiki.flashrecall.v2.audio

import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Pcm16FileCaptureSessionTest {
    @Test fun decodesBoundedLittleEndianPcm16() {
        val dir = createTempDirectory("voiceprint-pcm").toFile()
        val file = File(dir, "segment-1.pcm")
        file.writeBytes(byteArrayOf(0x00, 0x00, 0xff.toByte(), 0x7f, 0x00, 0x80.toByte()))
        val session = Pcm16FileCaptureSession(file, minSamples = 1, maxSamples = 3)
        assertTrue(session.start())
        assertArrayEquals(floatArrayOf(0f, 32767f / 32768f, -1f), session.stopAndTakeSamples(), 0.000001f)
        dir.deleteRecursively()
    }

    @Test fun rejectsTooShortOddOrOversizedInput() {
        val dir = createTempDirectory("voiceprint-pcm-invalid").toFile()
        val short = File(dir, "short.pcm").apply { writeBytes(byteArrayOf(1, 2)) }
        val odd = File(dir, "odd.pcm").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val big = File(dir, "big.pcm").apply { writeBytes(ByteArray(8)) }
        assertFalse(Pcm16FileCaptureSession(short, minSamples = 2, maxSamples = 3).start())
        assertFalse(Pcm16FileCaptureSession(odd, minSamples = 1, maxSamples = 3).start())
        assertFalse(Pcm16FileCaptureSession(big, minSamples = 1, maxSamples = 3).start())
        dir.deleteRecursively()
    }
}
