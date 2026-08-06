package com.qujindai.locowiki.flashrecall.v2.meeting

import java.util.UUID

data class QuestionThreadDraft(
    val threadId: String,
    val initiatorSpeakerId: String,
    val initiatorLabel: String,
    val canonicalQuestion: String,
    val utteranceIds: List<String>,
    val startedAtMs: Long,
    val lastUpdatedAtMs: Long,
    val confidence: Float,
)

data class ThreadUpdate(
    val thread: QuestionThreadDraft,
    val relationType: String,
    val created: Boolean,
)

class QuestionThreadAssembler(private val maxGapMs: Long = 90_000L) {
    private val continuationTokens = listOf("还有", "那么", "包含", "相比", "上一条", "刚才", "这个", "那套", "最终", "合同价", "预算价")

    fun consume(
        utteranceId: String,
        speakerLabel: String,
        atMs: Long,
        text: String,
        isQuestion: Boolean,
        openThread: QuestionThreadDraft?,
        speakerId: String = speakerLabel,
    ): ThreadUpdate {
        val relation = if (isQuestion) "FOLLOW_UP" else "CONDITION"
        val canJoin = openThread != null &&
            openThread.initiatorSpeakerId == speakerId &&
            atMs - openThread.lastUpdatedAtMs in 0..maxGapMs &&
            (isQuestion || isContinuation(text))
        if (canJoin) {
            val canonical = joinCanonical(openThread!!.canonicalQuestion, text)
            return ThreadUpdate(
                thread = openThread.copy(
                    canonicalQuestion = canonical,
                    utteranceIds = openThread.utteranceIds + utteranceId,
                    lastUpdatedAtMs = atMs,
                    confidence = (openThread.confidence + 0.05f).coerceAtMost(1f),
                ),
                relationType = relation,
                created = false,
            )
        }
        return ThreadUpdate(
            thread = QuestionThreadDraft(
                threadId = "thread_${UUID.randomUUID()}",
                initiatorSpeakerId = speakerId,
                initiatorLabel = speakerLabel,
                canonicalQuestion = text.trim(),
                utteranceIds = listOf(utteranceId),
                startedAtMs = atMs,
                lastUpdatedAtMs = atMs,
                confidence = if (isQuestion) 0.75f else 0.55f,
            ),
            relationType = if (isQuestion) "MAIN_QUESTION" else "CONDITION",
            created = true,
        )
    }

    fun isContinuation(text: String): Boolean {
        val normalized = text.trim()
        return normalized.length <= 24 && continuationTokens.any(normalized::contains)
    }

    private fun joinCanonical(existing: String, next: String): String {
        val cleanExisting = existing.trim().trimEnd('。', '；', ';')
        val cleanNext = next.trim().trimEnd('。', '；', ';')
        if (cleanNext.isBlank() || cleanExisting.contains(cleanNext)) return cleanExisting
        return "$cleanExisting；$cleanNext"
    }
}
