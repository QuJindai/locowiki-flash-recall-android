package com.qujindai.locowiki.flashrecall.v2.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class PcmRingBufferTest {
    @Test fun keepsNewestSamplesInOrder() {
        val ring = PcmRingBuffer(5)
        ring.write(floatArrayOf(1f, 2f, 3f))
        ring.write(floatArrayOf(4f, 5f, 6f))
        assertArrayEquals(floatArrayOf(2f, 3f, 4f, 5f, 6f), ring.snapshot(), 0f)
        assertEquals(5, ring.sampleCount())
    }

    @Test fun clearsMemoryView() {
        val ring = PcmRingBuffer(3)
        ring.write(floatArrayOf(1f, 2f))
        ring.clear()
        assertEquals(0, ring.sampleCount())
    }
}
