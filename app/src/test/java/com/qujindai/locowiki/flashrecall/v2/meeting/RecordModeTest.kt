package com.qujindai.locowiki.flashrecall.v2.meeting

import com.qujindai.locowiki.flashrecall.v2.domain.RecordMode
import org.junit.Assert.assertEquals
import org.junit.Test

class RecordModeTest {
    @Test fun defaultMeetingRecordModeIsTextOnly() {
        assertEquals(RecordMode.TEXT_ONLY, RecordMode.defaultMode())
    }
}
