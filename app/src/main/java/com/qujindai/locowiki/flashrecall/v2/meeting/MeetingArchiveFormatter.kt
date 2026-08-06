package com.qujindai.locowiki.flashrecall.v2.meeting

import com.qujindai.locowiki.flashrecall.v2.domain.ArchivedEvidence
import com.qujindai.locowiki.flashrecall.v2.domain.ArchivedQuery
import com.qujindai.locowiki.flashrecall.v2.domain.MeetingUtterance
import com.qujindai.locowiki.flashrecall.v2.domain.QuestionThreadSummary
import com.qujindai.locowiki.flashrecall.v2.data.SpeakerClusterEntity
import org.json.JSONArray
import org.json.JSONObject

class MeetingArchiveFormatter {
    fun transcriptJsonl(items: List<MeetingUtterance>): String = items.joinToString("\n") { item ->
        JSONObject()
            .put("utterance_id", item.utteranceId)
            .put("speaker_id", item.speakerId)
            .put("speaker_label", item.speakerLabel)
            .put("speaker_confidence", item.speakerConfidence)
            .put("speaker_manual", item.speakerManual)
            .put("start_ms", item.startMs)
            .put("end_ms", item.endMs)
            .put("text", item.text)
            .put("is_question", item.isQuestion)
            .put("target_self_score", item.targetSelfScore)
            .put("question_type", item.questionType.name)
            .put("thread_id", item.threadId)
            .put("asr_confidence", item.asrConfidence ?: JSONObject.NULL)
            .toString()
    }

    fun queriesJsonl(items: List<ArchivedQuery>): String = items.joinToString("\n") { item ->
        JSONObject()
            .put("query_id", item.queryId)
            .put("question", item.question)
            .put("answer", item.answer)
            .put("route", item.route)
            .put("end_to_end_ms", item.endToEndMs)
            .toString()
    }

    fun evidenceJsonl(items: List<ArchivedEvidence>): String = items.joinToString("\n") { item ->
        JSONObject()
            .put("evidence_id", item.evidenceId)
            .put("query_id", item.queryId)
            .put("content", item.content)
            .toString()
    }

    fun speakersJson(items: List<SpeakerClusterEntity>): String {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject()
                    .put("speaker_id", item.speakerId)
                    .put("speaker_label", item.speakerLabel)
                    .put("sample_count", item.sampleCount)
                    .put("confidence", item.confidence)
                    .put("manual_locked", item.manualLocked)
            )
        }
        return JSONObject().put("speakers", array).toString(2)
    }

    fun questionThreadsJsonl(items: List<QuestionThreadSummary>): String = items.joinToString("\n") { item ->
        JSONObject()
            .put("thread_id", item.threadId)
            .put("initiator_speaker_id", item.initiatorSpeakerId)
            .put("initiator_label", item.initiatorLabel)
            .put("canonical_question", item.canonicalQuestion)
            .put("status", item.status)
            .put("utterance_count", item.utteranceCount)
            .put("started_at_ms", item.startedAtMs)
            .put("last_updated_at_ms", item.lastUpdatedAtMs)
            .put("confidence", item.confidence)
            .toString()
    }

}
