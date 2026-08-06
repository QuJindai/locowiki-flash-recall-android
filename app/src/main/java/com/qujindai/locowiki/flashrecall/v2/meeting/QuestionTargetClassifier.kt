package com.qujindai.locowiki.flashrecall.v2.meeting

import com.qujindai.locowiki.flashrecall.v2.domain.QuestionType

data class QuestionAssessment(
    val type: QuestionType,
    val targetSelfScore: Float,
)

class QuestionTargetClassifier {
    private val questionTokens = listOf(
        "吗", "么", "呢", "什么", "多少", "几", "哪", "谁", "如何", "怎么", "为什么", "是否", "有没有", "能不能", "可不可以", "是不是", "何时", "哪里", "哪年",
        "what", "why", "how", "when", "where", "which", "who", "can ", "could ", "would ", "should "
    )
    private val directSelfTokens = listOf("你", "你们", "咱们", "工程部", "你这边", "你负责", "请你", "麻烦你")
    private val domainTokens = listOf("设备", "车型", "配置", "价格", "合同价", "预算", "合格率", "故障", "标准", "条款", "项目", "工厂", "供应商", "版本")
    private val continuationTokens = listOf("最终价格", "合同价", "预算价", "包含", "还有", "那么", "相比", "上一条", "刚才", "这个", "那套")

    fun isLikelyQuestion(text: String): Boolean {
        val normalized = text.trim().lowercase()
        if (normalized.isBlank()) return false
        if (normalized.endsWith("?") || normalized.endsWith("？")) return true
        return questionTokens.any(normalized::contains)
    }

    fun isContinuationCondition(text: String): Boolean {
        val normalized = text.trim().lowercase()
        return normalized.length <= 20 && continuationTokens.any(normalized::contains)
    }

    fun classify(
        text: String,
        speakerLabel: String,
        previousSpeakerLabel: String?,
        recentSelfContext: Boolean,
        hasOpenThreadForSpeaker: Boolean = false,
    ): QuestionAssessment {
        val question = isLikelyQuestion(text)
        val normalizedSpeaker = speakerLabel.uppercase()
        if (normalizedSpeaker == "SELF") {
            return QuestionAssessment(
                if (question) QuestionType.SELF_QUESTION else QuestionType.ANSWER_BY_SELF,
                0f,
            )
        }
        if (!question) {
            return if (hasOpenThreadForSpeaker && isContinuationCondition(text)) {
                QuestionAssessment(QuestionType.FOLLOW_UP_CONDITION, 0.55f)
            } else {
                QuestionAssessment(QuestionType.STATEMENT, 0f)
            }
        }

        var score = 0.38f
        if (directSelfTokens.any(text::contains)) score += 0.28f
        if (recentSelfContext || previousSpeakerLabel?.uppercase() == "SELF") score += 0.18f
        if (domainTokens.any(text::contains)) score += 0.12f
        if (normalizedSpeaker == "UNKNOWN" || normalizedSpeaker.isBlank()) score -= 0.08f
        score = score.coerceIn(0f, 1f)
        val type = when {
            score >= 0.65f -> QuestionType.QUESTION_TO_SELF
            score >= 0.35f -> QuestionType.QUESTION_UNRESOLVED
            else -> QuestionType.QUESTION_TO_OTHER
        }
        return QuestionAssessment(type, score)
    }

    fun shouldPromoteWhenSelfReplies(
        gapMs: Long,
        previousType: QuestionType,
        currentSpeakerLabel: String,
    ): Boolean = currentSpeakerLabel.uppercase() == "SELF" &&
        previousType == QuestionType.QUESTION_UNRESOLVED &&
        gapMs in 0..10_000L
}
