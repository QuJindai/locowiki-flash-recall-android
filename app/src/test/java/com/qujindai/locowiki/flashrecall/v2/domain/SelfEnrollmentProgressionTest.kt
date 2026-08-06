package com.qujindai.locowiki.flashrecall.v2.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SelfEnrollmentProgressionTest {
    @Test fun `accepted sample count only moves forward to three`() {
        val accepted = mutableListOf<Int>()
        repeat(3) { index ->
            accepted += index + 1
            assertEquals(index + 1, accepted.last())
            assertEquals((index + 2).coerceAtMost(3), SelfEnrollmentPolicy.nextSampleNumber(accepted.last(), 3))
        }
        assertEquals(listOf(1, 2, 3), accepted)
        assertFalse(SelfEnrollmentPolicy.canStart(true, false, SelfEnrollmentPhase.IDLE, 3, 3))
    }
}
