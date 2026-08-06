package com.qujindai.locowiki.flashrecall.v2.meeting

import android.content.Context
import com.qujindai.locowiki.flashrecall.v2.data.AppDatabase
import com.qujindai.locowiki.flashrecall.v2.data.EvidenceSnapshotEntity
import com.qujindai.locowiki.flashrecall.v2.data.MeetingSessionEntity
import com.qujindai.locowiki.flashrecall.v2.data.QueryRecordEntity
import com.qujindai.locowiki.flashrecall.v2.data.QuestionThreadEntity
import com.qujindai.locowiki.flashrecall.v2.data.QuestionThreadUtteranceEntity
import com.qujindai.locowiki.flashrecall.v2.data.UtteranceEntity
import com.qujindai.locowiki.flashrecall.v2.domain.AnswerCard
import com.qujindai.locowiki.flashrecall.v2.domain.ArchivedEvidence
import com.qujindai.locowiki.flashrecall.v2.domain.ArchivedQuery
import com.qujindai.locowiki.flashrecall.v2.domain.MeetingArchiveBundle
import com.qujindai.locowiki.flashrecall.v2.domain.MeetingContext
import com.qujindai.locowiki.flashrecall.v2.domain.MeetingSessionSummary
import com.qujindai.locowiki.flashrecall.v2.domain.MeetingUtterance
import com.qujindai.locowiki.flashrecall.v2.domain.QuestionThreadSummary
import com.qujindai.locowiki.flashrecall.v2.domain.QuestionType
import com.qujindai.locowiki.flashrecall.v2.domain.RecordMode
import org.json.JSONObject
import java.io.File
import java.io.OutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class MeetingArchiveRepository(
    private val context: Context,
    private val db: AppDatabase = AppDatabase.get(context),
) {
    private val formatter = MeetingArchiveFormatter()

    suspend fun startSession(meetingContext: MeetingContext, recordMode: RecordMode): MeetingSessionEntity {
        require(recordMode != RecordMode.NONE)
        val now = System.currentTimeMillis()
        val sessionId = "meeting_${now}_${UUID.randomUUID().toString().take(8)}"
        val title = listOf(meetingContext.project, meetingContext.topic)
            .map(String::trim)
            .filter(String::isNotBlank)
            .joinToString("-")
            .ifBlank { "未命名会议" }
        val entity = MeetingSessionEntity(
            sessionId = sessionId,
            title = title,
            startedAt = now,
            project = meetingContext.project,
            vehicle = meetingContext.vehicle,
            factory = meetingContext.factory,
            topic = meetingContext.topic,
            meetingYear = meetingContext.year,
            recordMode = recordMode.storageValue,
        )
        db.meetingSessionDao().insert(entity)
        return entity
    }

    fun audioFileFor(sessionId: String): File =
        File(context.filesDir, "meetings/$sessionId/audio.m4a").also { it.parentFile?.mkdirs() }

    suspend fun finishSession(sessionId: String, audioPath: String?) {
        db.meetingSessionDao().complete(sessionId, System.currentTimeMillis(), audioPath.orEmpty())
    }

    suspend fun appendUtterance(sessionId: String, item: MeetingUtterance) {
        db.utteranceDao().insert(
            UtteranceEntity(
                utteranceId = item.utteranceId,
                sessionId = sessionId,
                speakerId = item.speakerId,
                speakerLabel = item.speakerLabel,
                speakerConfidence = item.speakerConfidence,
                speakerManual = item.speakerManual,
                startMs = item.startMs,
                endMs = item.endMs,
                text = item.text,
                isQuestion = item.isQuestion,
                targetSelfScore = item.targetSelfScore,
                questionType = item.questionType.name,
                threadId = item.threadId,
                asrConfidence = item.asrConfidence,
            )
        )
    }

    suspend fun updateUtterance(item: MeetingUtterance) {
        db.utteranceDao().updateSpeaker(
            item.utteranceId,
            item.speakerId,
            item.speakerLabel,
            item.speakerConfidence,
            item.speakerManual,
        )
        db.utteranceDao().updateQuestionAssessment(
            item.utteranceId,
            item.targetSelfScore,
            item.questionType.name,
            item.isQuestion,
        )
        if (item.threadId.isNotBlank()) db.utteranceDao().updateThread(item.utteranceId, item.threadId)
    }

    suspend fun recentUtterances(sessionId: String, limit: Int = 20): List<MeetingUtterance> =
        db.utteranceDao().recentForSession(sessionId, limit).reversed().map(::toDomain)

    suspend fun latestUnresolved(sessionId: String, beforeMs: Long, afterMs: Long): MeetingUtterance? =
        db.utteranceDao().latestUnresolved(sessionId, beforeMs, afterMs)?.let(::toDomain)

    suspend fun upsertThread(summary: QuestionThreadSummary, utteranceId: String, relationType: String) {
        db.questionThreadDao().upsert(
            QuestionThreadEntity(
                threadId = summary.threadId,
                sessionId = currentSessionIdForUtterance(utteranceId),
                initiatorSpeakerId = summary.initiatorSpeakerId,
                initiatorLabel = summary.initiatorLabel,
                canonicalQuestion = summary.canonicalQuestion,
                status = summary.status,
                startedAtMs = summary.startedAtMs,
                lastUpdatedAt = summary.lastUpdatedAtMs,
                confidence = summary.confidence,
            )
        )
        db.questionThreadUtteranceDao().insert(
            QuestionThreadUtteranceEntity(
                threadId = summary.threadId,
                utteranceId = utteranceId,
                relationType = relationType,
                sequenceNo = db.questionThreadUtteranceDao().countForThread(summary.threadId),
            )
        )
        db.utteranceDao().updateThread(utteranceId, summary.threadId)
    }

    suspend fun latestOpenThread(sessionId: String, speakerId: String): QuestionThreadSummary? {
        val entity = db.questionThreadDao().latestOpenForSpeaker(sessionId, speakerId) ?: return null
        return toThreadSummary(entity)
    }

    suspend fun threadSummary(threadId: String): QuestionThreadSummary? =
        db.questionThreadDao().byId(threadId)?.let { toThreadSummary(it) }

    suspend fun allThreads(sessionId: String): List<QuestionThreadSummary> =
        db.questionThreadDao().allForSession(sessionId).map { toThreadSummary(it) }

    private suspend fun currentSessionIdForUtterance(utteranceId: String): String =
        db.utteranceDao().byId(utteranceId)?.sessionId ?: error("发言记录不存在")

    suspend fun archiveQuery(
        sessionId: String,
        sourceUtteranceIds: Set<String>,
        question: String,
        answer: AnswerCard,
        endToEndMs: Long,
    ): String {
        val queryId = "query_${UUID.randomUUID()}"
        db.queryRecordDao().insert(
            QueryRecordEntity(
                queryId = queryId,
                sessionId = sessionId,
                sourceUtteranceIds = sourceUtteranceIds.joinToString(","),
                displayQuestion = question,
                answerText = answer.answer,
                queryRoute = answer.timing.route,
                resultStatus = answer.status.name,
                endToEndMs = endToEndMs,
            )
        )
        db.evidenceSnapshotDao().insert(
            EvidenceSnapshotEntity(
                evidenceId = "evidence_${UUID.randomUUID()}",
                queryId = queryId,
                content = answer.evidence,
            )
        )
        return queryId
    }

    suspend fun latestSummary(): MeetingSessionSummary? {
        val session = db.meetingSessionDao().latest() ?: return null
        return MeetingSessionSummary(
            sessionId = session.sessionId,
            title = session.title,
            startedAt = session.startedAt,
            endedAt = session.endedAt,
            recordMode = RecordMode.fromStorage(session.recordMode),
            utteranceCount = db.utteranceDao().countForSession(session.sessionId),
            queryCount = db.queryRecordDao().countForSession(session.sessionId),
            audioAvailable = session.audioPath.isNotBlank() && File(session.audioPath).isFile,
        )
    }

    suspend fun buildArchiveBundle(sessionId: String): MeetingArchiveBundle {
        val session = db.meetingSessionDao().byId(sessionId) ?: error("会议记录不存在")
        val utterances = db.utteranceDao().allForSession(sessionId).map {
toDomain(it)
        }
        val queries = db.queryRecordDao().allForSession(sessionId).map {
            ArchivedQuery(it.queryId, it.displayQuestion, it.answerText, it.queryRoute, it.endToEndMs)
        }
        val evidence = db.evidenceSnapshotDao().allForSession(sessionId).map {
            ArchivedEvidence(it.evidenceId, it.queryId, it.content)
        }
        val metadata = JSONObject()
            .put("session_id", session.sessionId)
            .put("title", session.title)
            .put("started_at_epoch_ms", session.startedAt)
            .put("ended_at_epoch_ms", session.endedAt ?: JSONObject.NULL)
            .put("project", session.project)
            .put("vehicle", session.vehicle)
            .put("factory", session.factory)
            .put("topic", session.topic)
            .put("meeting_year", session.meetingYear)
            .put("record_mode", session.recordMode)
            .put("status", session.status)
            .put("utterance_count", utterances.size)
            .put("query_count", queries.size)
            .put("app_version", "0.3.0")
            .put("privacy_mode", true)
            .toString(2)
        val audioPath = session.audioPath.takeIf { it.isNotBlank() && File(it).isFile }
        val speakers = db.speakerClusterDao().allForSession(sessionId)
        val threads = allThreads(sessionId)
        return MeetingArchiveBundle(
            metadataJson = metadata,
            transcriptJsonl = formatter.transcriptJsonl(utterances),
            queriesJsonl = formatter.queriesJsonl(queries),
            evidenceJsonl = formatter.evidenceJsonl(evidence),
            speakersJson = formatter.speakersJson(speakers),
            questionThreadsJsonl = formatter.questionThreadsJsonl(threads),
            audioPath = audioPath,
        )
    }


    private fun toDomain(it: UtteranceEntity): MeetingUtterance = MeetingUtterance(
        utteranceId = it.utteranceId,
        startMs = it.startMs,
        endMs = it.endMs,
        text = it.correctedText.ifBlank { it.text },
        isQuestion = it.isQuestion,
        speakerId = it.speakerId,
        speakerLabel = it.speakerLabel,
        speakerConfidence = it.speakerConfidence,
        speakerManual = it.speakerManual,
        targetSelfScore = it.targetSelfScore,
        questionType = runCatching { QuestionType.valueOf(it.questionType) }.getOrDefault(QuestionType.STATEMENT),
        threadId = it.threadId,
        asrConfidence = it.asrConfidence,
    )

    private suspend fun toThreadSummary(it: QuestionThreadEntity): QuestionThreadSummary = QuestionThreadSummary(
        threadId = it.threadId,
        initiatorSpeakerId = it.initiatorSpeakerId,
        initiatorLabel = it.initiatorLabel,
        canonicalQuestion = it.canonicalQuestion,
        status = it.status,
        utteranceCount = db.questionThreadUtteranceDao().countForThread(it.threadId),
        startedAtMs = it.startedAtMs,
        lastUpdatedAtMs = it.lastUpdatedAt,
        confidence = it.confidence,
    )

    fun writeZip(output: OutputStream, bundle: MeetingArchiveBundle) {
        ZipOutputStream(output.buffered()).use { zip ->
            fun putText(name: String, value: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(value.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            putText("metadata.json", bundle.metadataJson)
            putText("transcript.jsonl", bundle.transcriptJsonl)
            putText("queries.jsonl", bundle.queriesJsonl)
            putText("evidence.jsonl", bundle.evidenceJsonl)
            putText("speakers.json", bundle.speakersJson)
            putText("question_threads.jsonl", bundle.questionThreadsJsonl)
            bundle.audioPath?.let { path ->
                val file = File(path)
                if (file.isFile) {
                    zip.putNextEntry(ZipEntry("audio.m4a"))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
    }
}
