package com.qujindai.locowiki.flashrecall.v2.domain

data class MeetingContext(
    val project: String = "E702",
    val vehicle: String = "",
    val factory: String = "繁荣工厂",
    val topic: String = "智驾标定",
    val year: Int = 2026,
    val competitor: String = "",
)

data class QueryIntent(
    val original: String,
    val normalized: String,
    val attributeKey: String,
    val requestedYear: Int?,
    val context: MeetingContext,
    val entityHints: List<String>,
)

enum class ResultStatus { FOUND, AMBIGUOUS, NOT_FOUND, CONFLICT, ERROR }

data class FactRecord(
    val factId: Long,
    val entityId: Long,
    val canonicalName: String,
    val entityType: String,
    val aliases: List<String>,
    val attributeKey: String,
    val attributeName: String,
    val valueText: String,
    val valueNumber: Double?,
    val unit: String,
    val year: Int?,
    val project: String,
    val factory: String,
    val topic: String,
    val definitionText: String,
    val status: String,
    val sourceName: String,
    val sourceLocation: String,
    val verifiedAt: String,
)

data class QueryStageTiming(
    val parseMs: Long = 0,
    val exactLookupMs: Long = 0,
    val ftsMs: Long = 0,
    val answerCompileMs: Long = 0,
    val route: String = "none",
)

data class AnswerCard(
    val status: ResultStatus,
    val answer: String,
    val evidence: String,
    val candidates: List<String> = emptyList(),
    val timing: QueryStageTiming = QueryStageTiming(),
)

data class AsrTiming(
    val speechStartNs: Long = 0,
    val speechEndNs: Long = 0,
    val firstPartialNs: Long = 0,
    val finalNs: Long = 0,
) {
    val firstPartialMs: Long
        get() = if (speechStartNs > 0 && firstPartialNs >= speechStartNs) (firstPartialNs - speechStartNs) / 1_000_000 else 0
    val endpointMs: Long
        get() = if (speechEndNs > 0 && finalNs >= speechEndNs) (finalNs - speechEndNs) / 1_000_000 else 0
}

data class ImportFact(
    val entityType: String,
    val canonicalName: String,
    val aliases: List<String>,
    val attributeKey: String,
    val attributeName: String,
    val valueText: String,
    val valueNumber: Double?,
    val unit: String,
    val year: Int?,
    val project: String,
    val factory: String,
    val topic: String,
    val definitionText: String,
    val status: String,
    val sourceName: String,
    val sourceLocation: String,
    val verifiedAt: String,
)

data class ImportPreview(
    val fileName: String,
    val facts: List<ImportFact>,
    val newCount: Int,
    val updateCount: Int,
    val conflictCount: Int,
    val rejectedCount: Int,
    val errors: List<String>,
)

data class DataStatus(
    val entities: Int = 0,
    val aliases: Int = 0,
    val facts: Int = 0,
    val verified: Int = 0,
    val drafts: Int = 0,
    val conflicts: Int = 0,
    val latencySamples: Int = 0,
    val p50Ms: Long = 0,
    val p95Ms: Long = 0,
    val meetings: Int = 0,
    val utterances: Int = 0,
    val archivedQueries: Int = 0,
)

data class DeviceDiagnostic(
    val model: String,
    val androidVersion: String,
    val abi: String,
    val processorCount: Int,
    val asrModel: String,
    val asrReady: Boolean,
    val vadReady: Boolean,
    val thermalStatus: Int,
    val systemRecognitionAvailable: Boolean,
    val systemOnDeviceRecognitionAvailable: Boolean,
)


enum class SpeakerMode(val storageValue: String) {
    SELF_ONLY("self_only"),
    SELF_AND_ABCD("self_and_abcd");

    companion object {
        fun fromStorage(value: String): SpeakerMode = entries.firstOrNull { it.storageValue == value } ?: SELF_ONLY
    }
}

enum class QuestionType {
    STATEMENT,
    SELF_QUESTION,
    QUESTION_TO_SELF,
    QUESTION_TO_OTHER,
    QUESTION_UNRESOLVED,
    FOLLOW_UP_CONDITION,
    ANSWER_BY_SELF,
    ANSWER_BY_OTHER,
}

enum class SelfEnrollmentPhase { IDLE, RECORDING, PROCESSING }

object SelfEnrollmentPolicy {
    fun canStart(
        modelReady: Boolean,
        meetingListening: Boolean,
        phase: SelfEnrollmentPhase,
        acceptedSamples: Int,
        requiredSamples: Int,
    ): Boolean = modelReady && !meetingListening && phase == SelfEnrollmentPhase.IDLE && acceptedSamples < requiredSamples

    fun nextSampleNumber(acceptedSamples: Int, requiredSamples: Int): Int =
        (acceptedSamples + 1).coerceIn(1, requiredSamples.coerceAtLeast(1))
}

data class SpeakerProfileState(
    val modelReady: Boolean = false,
    val enrolled: Boolean = false,
    val acceptedSamples: Int = 0,
    val requiredSamples: Int = 3,
    val threshold: Float = 0.70f,
    val message: String = "",
)

data class SpeakerClusterSummary(
    val speakerId: String,
    val label: String,
    val sampleCount: Int,
    val confidence: Float,
    val manualLocked: Boolean,
)

data class QuestionThreadSummary(
    val threadId: String,
    val initiatorSpeakerId: String,
    val initiatorLabel: String,
    val canonicalQuestion: String,
    val status: String,
    val utteranceCount: Int,
    val startedAtMs: Long,
    val lastUpdatedAtMs: Long,
    val confidence: Float,
)


enum class RecordMode(val storageValue: String) {
    NONE("none"),
    TEXT_ONLY("text_only"),
    TEXT_AND_AUDIO("text_and_audio");

    companion object {
        fun defaultMode(): RecordMode = TEXT_ONLY
        fun fromStorage(value: String): RecordMode = entries.firstOrNull { it.storageValue == value } ?: TEXT_ONLY
    }
}

data class MeetingUtterance(
    val utteranceId: String,
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val isQuestion: Boolean,
    val speakerId: String = "",
    val speakerLabel: String = "UNKNOWN",
    val speakerConfidence: Float = 0f,
    val speakerManual: Boolean = false,
    val targetSelfScore: Float = 0f,
    val questionType: QuestionType = QuestionType.STATEMENT,
    val threadId: String = "",
    val asrConfidence: Float? = null,
)

data class MeetingSessionSummary(
    val sessionId: String,
    val title: String,
    val startedAt: Long,
    val endedAt: Long?,
    val recordMode: RecordMode,
    val utteranceCount: Int,
    val queryCount: Int,
    val audioAvailable: Boolean,
)

data class ArchivedQuery(
    val queryId: String,
    val question: String,
    val answer: String,
    val route: String,
    val endToEndMs: Long,
)

data class ArchivedEvidence(
    val evidenceId: String,
    val queryId: String,
    val content: String,
)

data class MeetingArchiveBundle(
    val metadataJson: String,
    val transcriptJsonl: String,
    val queriesJsonl: String,
    val evidenceJsonl: String,
    val speakersJson: String = "{}",
    val questionThreadsJsonl: String = "",
    val audioPath: String?,
)
