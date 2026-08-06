package com.qujindai.locowiki.flashrecall.v2.meeting

import org.junit.Assert.*
import org.junit.Test

class QuestionThreadAssemblerTest {
    private val assembler = QuestionThreadAssembler(maxGapMs = 90_000L)

    @Test fun sameSpeakerFollowUpJoinsOpenThread() {
        val first = assembler.consume("u1", "A", 1_000L, "这套设备最终价格是多少？", true, null)
        val second = assembler.consume("u2", "A", 20_000L, "包含安装调试吗？", true, first.thread)
        assertEquals(first.thread.threadId, second.thread.threadId)
        assertTrue(second.thread.canonicalQuestion.contains("包含安装调试"))
    }

    @Test fun differentSpeakerStartsNewThread() {
        val first = assembler.consume("u1", "A", 1_000L, "这套设备多少钱？", true, null)
        val second = assembler.consume("u2", "B", 2_000L, "标准号是什么？", true, first.thread)
        assertNotEquals(first.thread.threadId, second.thread.threadId)
    }

    @Test fun expiredGapStartsNewThread() {
        val first = assembler.consume("u1", "A", 1_000L, "这套设备多少钱？", true, null)
        val second = assembler.consume("u2", "A", 100_000L, "包含安装吗？", true, first.thread)
        assertNotEquals(first.thread.threadId, second.thread.threadId)
    }

    @Test fun shortConditionJoinsAsCondition() {
        val first = assembler.consume("u1", "A", 1_000L, "这套设备多少钱？", true, null)
        val second = assembler.consume("u2", "A", 4_000L, "最终合同价", false, first.thread)
        assertEquals("CONDITION", second.relationType)
        assertEquals(first.thread.threadId, second.thread.threadId)
    }
}
