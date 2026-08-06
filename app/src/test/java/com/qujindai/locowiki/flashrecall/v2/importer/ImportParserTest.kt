package com.qujindai.locowiki.flashrecall.v2.importer

import org.junit.Assert.assertEquals
import org.junit.Test

class ImportParserTest {
    @Test fun csvSupportsQuotedCommaAndDraftDefault() {
        val csv = "entity_name,attribute_name,attribute_key,value_text,definition_text,aliases\n" +
            "设备A,价格,price,10,\"含税,含安装\",A设备|那套设备\n"
        val facts = ImportParser.parseCsv(csv)
        assertEquals(1, facts.size)
        assertEquals("含税,含安装", facts.first().definitionText)
        assertEquals("draft", facts.first().status)
        assertEquals(listOf("A设备", "那套设备"), facts.first().aliases)
    }

    @Test fun jsonDefaultsToDraft() {
        val facts = ImportParser.parseJson("[{\"entity_name\":\"设备A\",\"attribute_name\":\"价格\",\"value_text\":\"10\"}]")
        assertEquals("draft", facts.first().status)
    }
}
