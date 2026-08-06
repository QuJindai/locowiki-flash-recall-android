package com.qujindai.locowiki.flashrecall.v2.data

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query

@Dao
interface EntityDao {
    @Query("SELECT * FROM entities WHERE canonical_name = :name AND entity_type = :type LIMIT 1")
    suspend fun find(name: String, type: String): EntityEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: EntityEntity): Long

    @Query("SELECT COUNT(*) FROM entities")
    suspend fun count(): Int

    @Query("DELETE FROM entities")
    suspend fun deleteAll()
}

@Dao
interface AliasDao {
    @Query("SELECT * FROM entity_aliases WHERE enabled = 1 ORDER BY priority DESC, alias_text ASC")
    suspend fun allEnabled(): List<AliasEntity>

    @Query("SELECT alias_text FROM entity_aliases WHERE entity_id = :entityId AND enabled = 1 ORDER BY priority DESC")
    suspend fun aliasesFor(entityId: Long): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(alias: AliasEntity): Long

    @Query("SELECT COUNT(*) FROM entity_aliases WHERE enabled = 1")
    suspend fun count(): Int
}

@Dao
interface SourceDao {
    @Query("SELECT * FROM sources WHERE source_name = :name AND source_location = :location LIMIT 1")
    suspend fun find(name: String, location: String): SourceEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(source: SourceEntity): Long
}

@Dao
interface FactDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(fact: FactEntity): Long

    @Query("""
        SELECT f.fact_id, f.entity_id, e.canonical_name, e.entity_type,
               f.attribute_key, f.attribute_name, f.value_text, f.value_number,
               f.unit, f.year, f.project, f.factory, f.topic, f.definition_text,
               f.status, s.source_name, s.source_location, f.verified_at
        FROM facts f
        JOIN entities e ON e.entity_id = f.entity_id
        JOIN sources s ON s.source_id = f.source_id
        WHERE f.status IN ('verified', 'draft', 'conflict')
          AND (:attributeKey = 'general' OR f.attribute_key = :attributeKey)
          AND (:year IS NULL OR f.year = :year)
          AND (:project = '' OR f.project = :project)
          AND (:factory = '' OR f.factory = :factory)
          AND (:topic = '' OR f.topic = :topic)
        ORDER BY CASE f.status WHEN 'verified' THEN 0 WHEN 'draft' THEN 1 ELSE 2 END,
                 f.updated_at DESC
        LIMIT :limit
    """)
    suspend fun exactCandidates(
        attributeKey: String,
        year: Int?,
        project: String,
        factory: String,
        topic: String,
        limit: Int = 50,
    ): List<FactJoinedRow>

    @Query("""
        SELECT f.fact_id, f.entity_id, e.canonical_name, e.entity_type,
               f.attribute_key, f.attribute_name, f.value_text, f.value_number,
               f.unit, f.year, f.project, f.factory, f.topic, f.definition_text,
               f.status, s.source_name, s.source_location, f.verified_at
        FROM facts f
        JOIN entities e ON e.entity_id = f.entity_id
        JOIN sources s ON s.source_id = f.source_id
        WHERE f.fact_id IN (:ids)
    """)
    suspend fun byIds(ids: List<Long>): List<FactJoinedRow>

    @Query("""
        SELECT * FROM facts
        WHERE entity_id = :entityId AND attribute_key = :attributeKey
          AND ((:year IS NULL AND year IS NULL) OR year = :year)
          AND project = :project AND factory = :factory AND topic = :topic
        ORDER BY updated_at DESC LIMIT 1
    """)
    suspend fun findExisting(
        entityId: Long,
        attributeKey: String,
        year: Int?,
        project: String,
        factory: String,
        topic: String,
    ): FactEntity?

    @Query("SELECT COUNT(*) FROM facts")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM facts WHERE status = :status")
    suspend fun countByStatus(status: String): Int

    @Query("DELETE FROM facts")
    suspend fun deleteAll()
}

@Dao
interface FtsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: FactSearchFts)

    @Query("SELECT rowid FROM fact_search_fts WHERE fact_search_fts MATCH :query LIMIT :limit")
    suspend fun searchIds(query: String, limit: Int = 20): List<Long>

    @Query("DELETE FROM fact_search_fts")
    suspend fun deleteAll()
}

@Dao
interface ImportBatchDao {
    @Insert
    suspend fun insert(batch: ImportBatchEntity): Long
}

@Dao
interface LatencyDao {
    @Insert
    suspend fun insert(trace: LatencyTraceEntity): Long

    @Query("SELECT * FROM latency_traces ORDER BY created_at DESC LIMIT :limit")
    suspend fun latest(limit: Int = 500): List<LatencyTraceEntity>

    @Query("SELECT COUNT(*) FROM latency_traces")
    suspend fun count(): Int
}

@Dao
interface MeetingSessionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(session: MeetingSessionEntity)

    @Query("UPDATE meeting_sessions SET ended_at = :endedAt, status = 'completed', audio_path = :audioPath, updated_at = :endedAt WHERE session_id = :sessionId")
    suspend fun complete(sessionId: String, endedAt: Long, audioPath: String)

    @Query("SELECT * FROM meeting_sessions WHERE session_id = :sessionId LIMIT 1")
    suspend fun byId(sessionId: String): MeetingSessionEntity?

    @Query("SELECT * FROM meeting_sessions ORDER BY started_at DESC LIMIT 1")
    suspend fun latest(): MeetingSessionEntity?

    @Query("SELECT COUNT(*) FROM meeting_sessions")
    suspend fun count(): Int
}

@Dao
interface UtteranceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: UtteranceEntity)

    @Query("SELECT * FROM utterances WHERE utterance_id = :utteranceId LIMIT 1")
    suspend fun byId(utteranceId: String): UtteranceEntity?

    @Query("SELECT * FROM utterances WHERE session_id = :sessionId ORDER BY start_ms ASC")
    suspend fun allForSession(sessionId: String): List<UtteranceEntity>

    @Query("SELECT * FROM utterances WHERE session_id = :sessionId ORDER BY start_ms DESC LIMIT :limit")
    suspend fun recentForSession(sessionId: String, limit: Int = 20): List<UtteranceEntity>

    @Query("""
        UPDATE utterances
        SET speaker_id = :speakerId,
            speaker_label = :speakerLabel,
            speaker_confidence = :confidence,
            speaker_manual = :manual,
            updated_at = :updatedAt
        WHERE utterance_id = :utteranceId
    """)
    suspend fun updateSpeaker(
        utteranceId: String,
        speakerId: String,
        speakerLabel: String,
        confidence: Float,
        manual: Boolean,
        updatedAt: Long = System.currentTimeMillis(),
    )

    @Query("""
        UPDATE utterances
        SET target_self_score = :targetSelfScore,
            question_type = :questionType,
            is_question = :isQuestion,
            updated_at = :updatedAt
        WHERE utterance_id = :utteranceId
    """)
    suspend fun updateQuestionAssessment(
        utteranceId: String,
        targetSelfScore: Float,
        questionType: String,
        isQuestion: Boolean,
        updatedAt: Long = System.currentTimeMillis(),
    )

    @Query("UPDATE utterances SET thread_id = :threadId, updated_at = :updatedAt WHERE utterance_id = :utteranceId")
    suspend fun updateThread(utteranceId: String, threadId: String, updatedAt: Long = System.currentTimeMillis())

    @Query("""
        SELECT * FROM utterances
        WHERE session_id = :sessionId AND question_type = 'QUESTION_UNRESOLVED'
          AND end_ms <= :beforeMs AND end_ms >= :afterMs
        ORDER BY end_ms DESC LIMIT 1
    """)
    suspend fun latestUnresolved(sessionId: String, beforeMs: Long, afterMs: Long): UtteranceEntity?

    @Query("SELECT COUNT(*) FROM utterances WHERE session_id = :sessionId")
    suspend fun countForSession(sessionId: String): Int

    @Query("SELECT COUNT(*) FROM utterances")
    suspend fun count(): Int
}

@Dao
interface SpeakerProfileDao {
    @Query("SELECT * FROM speaker_profiles WHERE profile_type = 'SELF' LIMIT 1")
    suspend fun selfAny(): SpeakerProfileEntity?

    @Query("SELECT * FROM speaker_profiles WHERE profile_type = 'SELF' AND enabled = 1 LIMIT 1")
    suspend fun activeSelf(): SpeakerProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: SpeakerProfileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSample(sample: SpeakerEnrollmentSampleEntity)

    @Query("SELECT * FROM speaker_enrollment_samples WHERE profile_id = :profileId ORDER BY created_at ASC")
    suspend fun samples(profileId: String): List<SpeakerEnrollmentSampleEntity>

    @Query("DELETE FROM speaker_profiles WHERE profile_type = 'SELF'")
    suspend fun deleteSelf()
}

@Dao
interface SpeakerEmbeddingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: SpeakerEmbeddingEntity)

    @Query("SELECT * FROM speaker_embeddings WHERE utterance_id = :utteranceId LIMIT 1")
    suspend fun byUtterance(utteranceId: String): SpeakerEmbeddingEntity?

    @Query("SELECT * FROM speaker_embeddings WHERE session_id = :sessionId ORDER BY created_at ASC")
    suspend fun allForSession(sessionId: String): List<SpeakerEmbeddingEntity>
}

@Dao
interface SpeakerClusterDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: SpeakerClusterEntity)

    @Query("SELECT * FROM speaker_clusters WHERE session_id = :sessionId ORDER BY speaker_label ASC")
    suspend fun allForSession(sessionId: String): List<SpeakerClusterEntity>

    @Query("DELETE FROM speaker_clusters WHERE session_id = :sessionId AND manual_locked = 0")
    suspend fun deleteAutomaticForSession(sessionId: String)
}

@Dao
interface QuestionThreadDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: QuestionThreadEntity)

    @Query("SELECT * FROM question_threads WHERE thread_id = :threadId LIMIT 1")
    suspend fun byId(threadId: String): QuestionThreadEntity?

    @Query("SELECT * FROM question_threads WHERE session_id = :sessionId AND initiator_speaker_id = :speakerId AND status = 'OPEN' ORDER BY last_updated_at DESC LIMIT 1")
    suspend fun latestOpenForSpeaker(sessionId: String, speakerId: String): QuestionThreadEntity?

    @Query("SELECT * FROM question_threads WHERE session_id = :sessionId ORDER BY started_at_ms ASC")
    suspend fun allForSession(sessionId: String): List<QuestionThreadEntity>
}

@Dao
interface QuestionThreadUtteranceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: QuestionThreadUtteranceEntity)

    @Query("SELECT * FROM question_thread_utterances WHERE thread_id = :threadId ORDER BY sequence_no ASC")
    suspend fun allForThread(threadId: String): List<QuestionThreadUtteranceEntity>

    @Query("SELECT COUNT(*) FROM question_thread_utterances WHERE thread_id = :threadId")
    suspend fun countForThread(threadId: String): Int
}

@Dao
interface QueryRecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: QueryRecordEntity)

    @Query("SELECT * FROM query_records WHERE session_id = :sessionId ORDER BY created_at ASC")
    suspend fun allForSession(sessionId: String): List<QueryRecordEntity>

    @Query("SELECT COUNT(*) FROM query_records WHERE session_id = :sessionId")
    suspend fun countForSession(sessionId: String): Int

    @Query("SELECT COUNT(*) FROM query_records")
    suspend fun count(): Int
}

@Dao
interface EvidenceSnapshotDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: EvidenceSnapshotEntity)

    @Query("SELECT e.* FROM evidence_snapshots e JOIN query_records q ON q.query_id = e.query_id WHERE q.session_id = :sessionId ORDER BY e.created_at ASC")
    suspend fun allForSession(sessionId: String): List<EvidenceSnapshotEntity>
}
