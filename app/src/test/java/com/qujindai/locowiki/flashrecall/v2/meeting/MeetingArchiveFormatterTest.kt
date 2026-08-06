package com.qujindai.locowiki.flashrecall.v2.meeting

import com.qujindai.locowiki.flashrecall.v2.domain.ArchivedEvidence
import com.qujindai.locowiki.flashrecall.v2.domain.ArchivedQuery
import com.qujindai.locowiki.flashrecall.v2.domain.MeetingUtterance
import org.junit.Assert.assertTrue
import org.junit.Test

class MeetingArchiveFormatterTest {
    @Test fun exportContainsTranscriptQueryAndEvidence() {
        val formatter = MeetingArchiveFormatter()
        val transcript = formatter.transcriptJsonl(listOf(MeetingUtterance("u1", 1000, 3000, "这个设备多少钱", true)))
        val queries = formatter.queriesJsonl(listOf(ArchivedQuery("q1", "这个设备多少钱", "107.6万元", "exact", 1200)))
        val evidence = formatter.evidenceJsonl(listOf(ArchivedEvidence("e1", "q1", "采购定点表 第12页")))
        assertTrue(transcript.contains("\"utterance_id\":\"u1\""))
        assertTrue(queries.contains("\"query_id\":\"q1\""))
        assertTrue(evidence.contains("采购定点表 第12页"))
    }
}
