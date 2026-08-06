package com.qujindai.locowiki.flashrecall.v2.query

import com.qujindai.locowiki.flashrecall.v2.domain.MeetingContext
import org.junit.Assert.assertEquals
import org.junit.Test

class QueryParserTest {
    private val parser = QueryParser()

    @Test fun parsesRelativeYearAndPrice() {
        val result = parser.parse("去年那套设备多少钱", MeetingContext(project = "E702", year = 2026), 2026)
        assertEquals(2025, result.requestedYear)
        assertEquals("price", result.attributeKey)
    }

    @Test fun parsesStandardAndQuality() {
        assertEquals("standard_number", parser.parse("这个标准的标准号是什么", MeetingContext(), 2026).attributeKey)
        assertEquals("quality_rate", parser.parse("上年度一次合格率", MeetingContext(), 2026).attributeKey)
    }
}
