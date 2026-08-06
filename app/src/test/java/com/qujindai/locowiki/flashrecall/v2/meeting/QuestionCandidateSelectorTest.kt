package com.qujindai.locowiki.flashrecall.v2.meeting

import com.qujindai.locowiki.flashrecall.v2.domain.MeetingUtterance
import com.qujindai.locowiki.flashrecall.v2.domain.QuestionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionCandidateSelectorTest {
    private val selector = QuestionCandidateSelector()

    @Test fun prefersLatestLikelyQuestionOverLaterStatement() {
        val items = listOf(
            MeetingUtterance("u1", 1000, 4000, "去年那套设备多少钱", true),
            MeetingUtterance("u2", 5000, 7000, "最终合同价包含安装调试", false),
        )
        assertEquals("u1", selector.defaultCandidate(items)?.utteranceId)
    }

    @Test fun excludesSelfQuestionFromDefaultCandidate() {
        val items = listOf(
            MeetingUtterance("u1", 1000, 3000, "领导问的价格是多少？", true, speakerLabel = "A", questionType = QuestionType.QUESTION_TO_SELF),
            MeetingUtterance("u2", 4000, 6000, "我再问一下标准号是什么？", true, speakerLabel = "SELF", questionType = QuestionType.SELF_QUESTION),
        )
        assertEquals("u1", selector.defaultCandidate(items)?.utteranceId)
    }

    @Test fun recognisesChineseAndEnglishQuestionForms() {
        assertTrue(selector.isLikelyQuestion("这个标准编号是什么"))
        assertTrue(selector.isLikelyQuestion("M9有没有激光雷达"))
        assertTrue(selector.isLikelyQuestion("what is the contract price?"))
        assertFalse(selector.isLikelyQuestion("最终合同价包含安装调试"))
    }

    @Test fun mergesSelectedUtterancesChronologically() {
        val items = listOf(
            MeetingUtterance("u2", 5000, 7000, "最终合同价", false),
            MeetingUtterance("u1", 1000, 4000, "这个设备多少钱", true),
            MeetingUtterance("u3", 8000, 9000, "包含安装调试吗", true),
        )
        assertEquals("这个设备多少钱；最终合同价；包含安装调试吗", selector.mergeSelected(items, setOf("u3", "u1", "u2")))
    }
}
