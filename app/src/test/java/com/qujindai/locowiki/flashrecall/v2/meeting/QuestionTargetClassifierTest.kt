package com.qujindai.locowiki.flashrecall.v2.meeting

import com.qujindai.locowiki.flashrecall.v2.domain.QuestionType
import org.junit.Assert.*
import org.junit.Test

class QuestionTargetClassifierTest {
    private val classifier = QuestionTargetClassifier()

    @Test fun selfQuestionIsExcluded() {
        val result = classifier.classify("这个设备多少钱？", "SELF", null, false)
        assertEquals(QuestionType.SELF_QUESTION, result.type)
    }

    @Test fun directOtherQuestionTargetsSelf() {
        val result = classifier.classify("你们这个设备最终合同价是多少？", "A", "SELF", true)
        assertEquals(QuestionType.QUESTION_TO_SELF, result.type)
        assertTrue(result.targetSelfScore >= 0.65f)
    }

    @Test fun ambiguousOtherQuestionStaysUnresolved() {
        val result = classifier.classify("这个多少钱？", "B", null, false)
        assertEquals(QuestionType.QUESTION_UNRESOLVED, result.type)
    }

    @Test fun statementIsNotQuestion() {
        val result = classifier.classify("这个设备已经完成验收", "A", "SELF", true)
        assertEquals(QuestionType.STATEMENT, result.type)
    }

    @Test fun selfReplyPromotesRecentUnresolvedQuestion() {
        assertTrue(classifier.shouldPromoteWhenSelfReplies(5_000L, QuestionType.QUESTION_UNRESOLVED, "SELF"))
        assertFalse(classifier.shouldPromoteWhenSelfReplies(15_000L, QuestionType.QUESTION_UNRESOLVED, "SELF"))
    }
}
