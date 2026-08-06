package com.qujindai.locowiki.flashrecall.v2.data

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Fts5
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(tableName = "entities", indices = [Index(value = ["canonical_name", "entity_type"], unique = true)])
data class EntityEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "entity_id") val entityId: Long = 0,
    @ColumnInfo(name = "canonical_name") val canonicalName: String,
    @ColumnInfo(name = "entity_type") val entityType: String,
    val status: String = "active",
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "entity_aliases",
    foreignKeys = [ForeignKey(
        entity = EntityEntity::class,
        parentColumns = ["entity_id"],
        childColumns = ["entity_id"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index(value = ["entity_id"]), Index(value = ["alias_text"], unique = true)],
)
data class AliasEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "alias_id") val aliasId: Long = 0,
    @ColumnInfo(name = "entity_id") val entityId: Long,
    @ColumnInfo(name = "alias_text") val aliasText: String,
    @ColumnInfo(name = "alias_type") val aliasType: String = "spoken",
    val priority: Int = 50,
    val enabled: Boolean = true,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "sources", indices = [Index(value = ["source_name", "source_location"], unique = true)])
data class SourceEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "source_id") val sourceId: Long = 0,
    @ColumnInfo(name = "source_name") val sourceName: String,
    @ColumnInfo(name = "source_type") val sourceType: String = "record",
    @ColumnInfo(name = "source_location") val sourceLocation: String,
    @ColumnInfo(name = "file_hash") val fileHash: String = "",
    @ColumnInfo(name = "source_date") val sourceDate: String = "",
    val owner: String = "",
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "facts",
    foreignKeys = [
        ForeignKey(entity = EntityEntity::class, parentColumns = ["entity_id"], childColumns = ["entity_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = SourceEntity::class, parentColumns = ["source_id"], childColumns = ["source_id"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [
        Index(value = ["entity_id"]), Index(value = ["source_id"]),
        Index(value = ["attribute_key", "year", "project", "factory", "topic", "status"]),
    ],
)
data class FactEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "fact_id") val factId: Long = 0,
    @ColumnInfo(name = "entity_id") val entityId: Long,
    @ColumnInfo(name = "attribute_key") val attributeKey: String,
    @ColumnInfo(name = "attribute_name") val attributeName: String,
    @ColumnInfo(name = "value_text") val valueText: String,
    @ColumnInfo(name = "value_number") val valueNumber: Double? = null,
    val unit: String = "",
    val year: Int? = null,
    @ColumnInfo(name = "period_start") val periodStart: String = "",
    @ColumnInfo(name = "period_end") val periodEnd: String = "",
    val project: String = "",
    val factory: String = "",
    val topic: String = "",
    @ColumnInfo(name = "definition_text") val definitionText: String = "",
    val status: String = "draft",
    @ColumnInfo(name = "source_id") val sourceId: Long,
    @ColumnInfo(name = "verified_at") val verifiedAt: String = "",
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
)

@Fts5(tokenizer = "unicode61")
@Entity(tableName = "fact_search_fts")
data class FactSearchFts(
    @PrimaryKey @ColumnInfo(name = "rowid") val rowId: Long,
    @ColumnInfo(name = "canonical_name") val canonicalName: String,
    val aliases: String,
    @ColumnInfo(name = "attribute_name") val attributeName: String,
    @ColumnInfo(name = "value_text") val valueText: String,
    val project: String,
    val factory: String,
    val topic: String,
    @ColumnInfo(name = "source_name") val sourceName: String,
)

@Entity(tableName = "import_batches")
data class ImportBatchEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "batch_id") val batchId: Long = 0,
    @ColumnInfo(name = "file_name") val fileName: String,
    @ColumnInfo(name = "file_hash") val fileHash: String,
    @ColumnInfo(name = "imported_at") val importedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "record_count") val recordCount: Int,
    @ColumnInfo(name = "new_count") val newCount: Int,
    @ColumnInfo(name = "update_count") val updateCount: Int,
    @ColumnInfo(name = "conflict_count") val conflictCount: Int,
    @ColumnInfo(name = "rejected_count") val rejectedCount: Int,
    val status: String,
)

@Entity(tableName = "latency_traces", indices = [Index(value = ["created_at"])])
data class LatencyTraceEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "trace_id") val traceId: Long = 0,
    @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "query_text") val queryText: String,
    @ColumnInfo(name = "model_name") val modelName: String,
    @ColumnInfo(name = "model_precision") val modelPrecision: String,
    @ColumnInfo(name = "thread_count") val threadCount: Int,
    @ColumnInfo(name = "fact_count") val factCount: Int,
    @ColumnInfo(name = "alias_count") val aliasCount: Int,
    @ColumnInfo(name = "thermal_status") val thermalStatus: Int,
    @ColumnInfo(name = "cold_or_warm") val coldOrWarm: String,
    @ColumnInfo(name = "query_route") val queryRoute: String,
    @ColumnInfo(name = "speech_start_ns") val speechStartNs: Long,
    @ColumnInfo(name = "speech_end_ns") val speechEndNs: Long,
    @ColumnInfo(name = "asr_first_partial_ns") val asrFirstPartialNs: Long,
    @ColumnInfo(name = "asr_final_ns") val asrFinalNs: Long,
    @ColumnInfo(name = "asr_first_partial_ms") val asrFirstPartialMs: Long,
    @ColumnInfo(name = "asr_endpoint_ms") val asrEndpointMs: Long,
    @ColumnInfo(name = "query_parse_ms") val queryParseMs: Long,
    @ColumnInfo(name = "exact_lookup_ms") val exactLookupMs: Long,
    @ColumnInfo(name = "fts_ms") val ftsMs: Long,
    @ColumnInfo(name = "answer_compile_ms") val answerCompileMs: Long,
    @ColumnInfo(name = "end_to_end_ms") val endToEndMs: Long,
    @ColumnInfo(name = "result_status") val resultStatus: String,
    @ColumnInfo(name = "error_code") val errorCode: String = "",
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
)

data class FactJoinedRow(
    @ColumnInfo(name = "fact_id") val factId: Long,
    @ColumnInfo(name = "entity_id") val entityId: Long,
    @ColumnInfo(name = "canonical_name") val canonicalName: String,
    @ColumnInfo(name = "entity_type") val entityType: String,
    @ColumnInfo(name = "attribute_key") val attributeKey: String,
    @ColumnInfo(name = "attribute_name") val attributeName: String,
    @ColumnInfo(name = "value_text") val valueText: String,
    @ColumnInfo(name = "value_number") val valueNumber: Double?,
    val unit: String,
    val year: Int?,
    val project: String,
    val factory: String,
    val topic: String,
    @ColumnInfo(name = "definition_text") val definitionText: String,
    val status: String,
    @ColumnInfo(name = "source_name") val sourceName: String,
    @ColumnInfo(name = "source_location") val sourceLocation: String,
    @ColumnInfo(name = "verified_at") val verifiedAt: String,
)

@Entity(tableName = "meeting_sessions", indices = [Index(value = ["started_at"]), Index(value = ["status"])])
data class MeetingSessionEntity(
    @PrimaryKey @ColumnInfo(name = "session_id") val sessionId: String,
    val title: String,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "ended_at") val endedAt: Long? = null,
    val project: String,
    val vehicle: String,
    val factory: String,
    val topic: String,
    @ColumnInfo(name = "meeting_year") val meetingYear: Int,
    @ColumnInfo(name = "record_mode") val recordMode: String,
    val status: String = "active",
    @ColumnInfo(name = "audio_path") val audioPath: String = "",
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "utterances",
    foreignKeys = [ForeignKey(
        entity = MeetingSessionEntity::class,
        parentColumns = ["session_id"],
        childColumns = ["session_id"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [
        Index(value = ["session_id", "start_ms"]),
        Index(value = ["is_question"]),
        Index(value = ["speaker_id"]),
        Index(value = ["question_type"]),
        Index(value = ["thread_id"]),
    ],
)
data class UtteranceEntity(
    @PrimaryKey @ColumnInfo(name = "utterance_id") val utteranceId: String,
    @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "speaker_id", defaultValue = "") val speakerId: String = "",
    @ColumnInfo(name = "speaker_label", defaultValue = "UNKNOWN") val speakerLabel: String = "UNKNOWN",
    @ColumnInfo(name = "speaker_confidence", defaultValue = "0") val speakerConfidence: Float = 0f,
    @ColumnInfo(name = "speaker_manual", defaultValue = "0") val speakerManual: Boolean = false,
    @ColumnInfo(name = "start_ms") val startMs: Long,
    @ColumnInfo(name = "end_ms") val endMs: Long,
    val text: String,
    @ColumnInfo(name = "corrected_text") val correctedText: String = "",
    @ColumnInfo(name = "is_question") val isQuestion: Boolean,
    @ColumnInfo(name = "target_self_score", defaultValue = "0") val targetSelfScore: Float = 0f,
    @ColumnInfo(name = "question_type", defaultValue = "STATEMENT") val questionType: String = "STATEMENT",
    @ColumnInfo(name = "thread_id", defaultValue = "") val threadId: String = "",
    @ColumnInfo(name = "asr_confidence") val asrConfidence: Float? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "speaker_profiles", indices = [Index(value = ["profile_type", "enabled"])])
data class SpeakerProfileEntity(
    @PrimaryKey @ColumnInfo(name = "profile_id") val profileId: String,
    @ColumnInfo(name = "profile_type") val profileType: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "model_id") val modelId: String,
    @ColumnInfo(name = "prototype_blob") val prototypeBlob: ByteArray,
    @ColumnInfo(name = "sample_count") val sampleCount: Int,
    val threshold: Float,
    val enabled: Boolean = true,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "speaker_enrollment_samples",
    foreignKeys = [ForeignKey(
        entity = SpeakerProfileEntity::class,
        parentColumns = ["profile_id"],
        childColumns = ["profile_id"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index(value = ["profile_id", "created_at"])],
)
data class SpeakerEnrollmentSampleEntity(
    @PrimaryKey @ColumnInfo(name = "sample_id") val sampleId: String,
    @ColumnInfo(name = "profile_id") val profileId: String,
    @ColumnInfo(name = "embedding_blob") val embeddingBlob: ByteArray,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "speaker_embeddings",
    foreignKeys = [ForeignKey(
        entity = UtteranceEntity::class,
        parentColumns = ["utterance_id"],
        childColumns = ["utterance_id"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index(value = ["session_id", "created_at"]), Index(value = ["utterance_id"], unique = true)],
)
data class SpeakerEmbeddingEntity(
    @PrimaryKey @ColumnInfo(name = "embedding_id") val embeddingId: String,
    @ColumnInfo(name = "utterance_id") val utteranceId: String,
    @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "model_id") val modelId: String,
    val dimensions: Int,
    @ColumnInfo(name = "vector_blob") val vectorBlob: ByteArray,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "speaker_clusters",
    foreignKeys = [ForeignKey(
        entity = MeetingSessionEntity::class,
        parentColumns = ["session_id"],
        childColumns = ["session_id"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index(value = ["session_id", "speaker_label"], unique = true)],
)
data class SpeakerClusterEntity(
    @PrimaryKey @ColumnInfo(name = "speaker_id") val speakerId: String,
    @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "speaker_label") val speakerLabel: String,
    @ColumnInfo(name = "centroid_blob") val centroidBlob: ByteArray,
    @ColumnInfo(name = "sample_count") val sampleCount: Int,
    val confidence: Float,
    @ColumnInfo(name = "manual_locked") val manualLocked: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "question_threads",
    foreignKeys = [ForeignKey(
        entity = MeetingSessionEntity::class,
        parentColumns = ["session_id"],
        childColumns = ["session_id"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index(value = ["session_id", "last_updated_at"]), Index(value = ["initiator_speaker_id"])],
)
data class QuestionThreadEntity(
    @PrimaryKey @ColumnInfo(name = "thread_id") val threadId: String,
    @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "initiator_speaker_id") val initiatorSpeakerId: String,
    @ColumnInfo(name = "initiator_label") val initiatorLabel: String,
    @ColumnInfo(name = "canonical_question") val canonicalQuestion: String,
    val status: String = "OPEN",
    @ColumnInfo(name = "started_at_ms") val startedAtMs: Long,
    @ColumnInfo(name = "last_updated_at") val lastUpdatedAt: Long,
    val confidence: Float,
    @ColumnInfo(name = "manually_confirmed") val manuallyConfirmed: Boolean = false,
)

@Entity(
    tableName = "question_thread_utterances",
    primaryKeys = ["thread_id", "utterance_id"],
    foreignKeys = [
        ForeignKey(entity = QuestionThreadEntity::class, parentColumns = ["thread_id"], childColumns = ["thread_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = UtteranceEntity::class, parentColumns = ["utterance_id"], childColumns = ["utterance_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["utterance_id"]), Index(value = ["thread_id", "sequence_no"])],
)
data class QuestionThreadUtteranceEntity(
    @ColumnInfo(name = "thread_id") val threadId: String,
    @ColumnInfo(name = "utterance_id") val utteranceId: String,
    @ColumnInfo(name = "relation_type") val relationType: String,
    @ColumnInfo(name = "sequence_no") val sequenceNo: Int,
)

@Entity(
    tableName = "query_records",
    foreignKeys = [ForeignKey(
        entity = MeetingSessionEntity::class,
        parentColumns = ["session_id"],
        childColumns = ["session_id"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index(value = ["session_id", "created_at"])],
)
data class QueryRecordEntity(
    @PrimaryKey @ColumnInfo(name = "query_id") val queryId: String,
    @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "source_utterance_ids") val sourceUtteranceIds: String,
    @ColumnInfo(name = "display_question") val displayQuestion: String,
    @ColumnInfo(name = "answer_text") val answerText: String,
    @ColumnInfo(name = "query_route") val queryRoute: String,
    @ColumnInfo(name = "result_status") val resultStatus: String,
    @ColumnInfo(name = "end_to_end_ms") val endToEndMs: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "evidence_snapshots",
    foreignKeys = [ForeignKey(
        entity = QueryRecordEntity::class,
        parentColumns = ["query_id"],
        childColumns = ["query_id"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index(value = ["query_id"])],
)
data class EvidenceSnapshotEntity(
    @PrimaryKey @ColumnInfo(name = "evidence_id") val evidenceId: String,
    @ColumnInfo(name = "query_id") val queryId: String,
    val content: String,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
)
