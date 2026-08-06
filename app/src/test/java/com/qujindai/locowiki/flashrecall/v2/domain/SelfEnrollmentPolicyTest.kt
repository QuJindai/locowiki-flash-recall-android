package com.qujindai.locowiki.flashrecall.v2.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelfEnrollmentPolicyTest {
    @Test fun `can enroll before meeting`() = assertTrue(
        SelfEnrollmentPolicy.canStart(true, false, SelfEnrollmentPhase.IDLE, 0, 3)
    )

    @Test fun `cannot overlap meeting processing or completed profile`() {
        assertFalse(SelfEnrollmentPolicy.canStart(true, true, SelfEnrollmentPhase.IDLE, 0, 3))
        assertFalse(SelfEnrollmentPolicy.canStart(true, false, SelfEnrollmentPhase.PROCESSING, 1, 3))
        assertFalse(SelfEnrollmentPolicy.canStart(true, false, SelfEnrollmentPhase.IDLE, 3, 3))
    }

    @Test fun `sample number advances one two three`() {
        assertEquals(1, SelfEnrollmentPolicy.nextSampleNumber(0, 3))
        assertEquals(2, SelfEnrollmentPolicy.nextSampleNumber(1, 3))
        assertEquals(3, SelfEnrollmentPolicy.nextSampleNumber(2, 3))
        assertEquals(3, SelfEnrollmentPolicy.nextSampleNumber(3, 3))
    }
}
