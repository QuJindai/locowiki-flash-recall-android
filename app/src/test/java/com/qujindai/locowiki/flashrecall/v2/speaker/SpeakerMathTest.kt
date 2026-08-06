package com.qujindai.locowiki.flashrecall.v2.speaker

import org.junit.Assert.*
import org.junit.Test

class SpeakerMathTest {
    @Test fun cosineOfSameDirectionIsOne() {
        assertEquals(1f, SpeakerMath.cosine(floatArrayOf(1f, 2f), floatArrayOf(2f, 4f)), 1e-5f)
    }

    @Test fun averageNormalizesPrototype() {
        val value = SpeakerMath.averageNormalized(listOf(floatArrayOf(1f, 0f), floatArrayOf(1f, 0f)))
        assertArrayEquals(floatArrayOf(1f, 0f), value, 1e-5f)
    }

    @Test fun floatBlobRoundTrip() {
        val original = floatArrayOf(-0.25f, 0f, 0.5f, 1f)
        assertArrayEquals(original, SpeakerMath.fromBlob(SpeakerMath.toBlob(original)), 0f)
    }
}
