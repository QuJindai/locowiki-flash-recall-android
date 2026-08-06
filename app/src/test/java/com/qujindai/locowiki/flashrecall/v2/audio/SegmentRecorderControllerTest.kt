package com.qujindai.locowiki.flashrecall.v2.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SegmentRecorderControllerTest {
    @Test fun createsAndClosesADistinctSessionForEverySegment() {
        val sessions = mutableListOf<FakeSession>()
        val requested = mutableListOf<Int>()
        val controller = SegmentRecorderController { number ->
            requested += number
            FakeSession(floatArrayOf(number.toFloat())).also(sessions::add)
        }
        assertTrue(controller.start(1))
        assertFalse(controller.start(1))
        assertArrayEquals(floatArrayOf(1f), controller.stopAndTakeSamples(), 0f)
        assertTrue(sessions[0].closed)
        assertTrue(controller.start(2))
        assertArrayEquals(floatArrayOf(2f), controller.stopAndTakeSamples(), 0f)
        assertTrue(sessions[1].closed)
        assertEquals(listOf(1, 2), requested)
    }

    private class FakeSession(private val samples: FloatArray) : SegmentCaptureSession {
        var closed = false
        override fun start() = true
        override fun stopAndTakeSamples() = samples
        override fun close() { closed = true }
    }
}
