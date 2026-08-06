package com.qujindai.locowiki.flashrecall.v2.meeting

import com.qujindai.locowiki.flashrecall.v2.domain.MeetingUtterance
import com.qujindai.locowiki.flashrecall.v2.domain.QuestionType

class QuestionCandidateSelector {
    private val questionTokens = listOf(
        "吗", "么", "呢", "什么", "多少", "几", "哪", "谁", "如何", "怎么", "为什么", "是否", "有没有", "能不能", "可不可以", "是不是", "何时", "哪里", "哪年", "what", "why", "how", "when", "where", "which", "who", "is ", "are ", "does ", "do ", "can ", "could ", "would ", "should "
    )

    fun isLikelyQuestion(text: String): Boolean {
        val normalized = text.trim().lowercase()
        if (normalized.isBlank()) return false
        if (normalized.endsWith("?") || normalized.endsWith("？")) return true
        return questionTokens.any { token -> normalized.contains(token) }
    }

    fun defaultCandidate(items: List<MeetingUtterance>): MeetingUtterance? =
        items.asReversed().firstOrNull {
            it.speakerLabel != "SELF" && it.questionType in preferredTypes
        } ?: items.asReversed().firstOrNull {
            it.speakerLabel != "SELF" && (it.isQuestion || isLikelyQuestion(it.text))
        }

    fun mergeSelected(items: List<MeetingUtterance>, selectedIds: Set<String>): String =
        items.asSequence()
            .filter { it.utteranceId in selectedIds }
            .sortedBy { it.startMs }
            .map { it.text.trim().trimEnd('。', '；', ';') }
            .filter { it.isNotBlank() }
            .joinToString("；")

    companion object {
        private val preferredTypes = setOf(
            QuestionType.QUESTION_TO_SELF,
            QuestionType.QUESTION_UNRESOLVED,
            QuestionType.FOLLOW_UP_CONDITION,
        )
    }
}
