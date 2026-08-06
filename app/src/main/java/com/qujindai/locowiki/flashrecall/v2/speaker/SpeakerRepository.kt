package com.qujindai.locowiki.flashrecall.v2.speaker

import android.content.Context
import com.qujindai.locowiki.flashrecall.v2.data.*
import com.qujindai.locowiki.flashrecall.v2.domain.SpeakerClusterSummary
import com.qujindai.locowiki.flashrecall.v2.domain.SpeakerMode
import com.qujindai.locowiki.flashrecall.v2.domain.SpeakerProfileState
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class SpeakerAnnotation(
    val speakerId: String,
    val speakerLabel: String,
    val confidence: Float,
    val identity: SpeakerIdentity,
)

class SpeakerRepository(
    context: Context,
    private val db: AppDatabase = AppDatabase.get(context),
    private val policy: SpeakerDecisionPolicy = SpeakerDecisionPolicy(),
) {
    companion object {
        const val SELF_PROFILE_ID = "profile_self"
        const val REQUIRED_SAMPLES = 3
    }

    private val clusterers = ConcurrentHashMap<String, OnlineSpeakerClusterer>()
    private val enrollmentSerial = SelfEnrollmentSerialExecutor()

    suspend fun profileState(modelReady: Boolean, message: String = ""): SpeakerProfileState {
        val profile = db.speakerProfileDao().selfAny()
        return SpeakerProfileState(
            modelReady = modelReady,
            enrolled = profile?.enabled == true && profile.sampleCount >= REQUIRED_SAMPLES,
            acceptedSamples = profile?.sampleCount ?: 0,
            requiredSamples = REQUIRED_SAMPLES,
            threshold = profile?.threshold ?: policy.selfThreshold,
            message = message,
        )
    }

    suspend fun addSelfSample(embedding: FloatArray): SpeakerProfileState = enrollmentSerial.run {
        require(embedding.isNotEmpty())
        val dao = db.speakerProfileDao()
        val existing = dao.selfAny()
        if (existing == null) {
            dao.upsert(
                SpeakerProfileEntity(
                    profileId = SELF_PROFILE_ID,
                    profileType = "SELF",
                    displayName = "我",
                    modelId = SherpaSpeakerEngine.MODEL_ID,
                    prototypeBlob = SpeakerMath.toBlob(embedding),
                    sampleCount = 0,
                    threshold = policy.selfThreshold,
                    enabled = false,
                )
            )
        }
        dao.insertSample(
            SpeakerEnrollmentSampleEntity(
                sampleId = "sample_${UUID.randomUUID()}",
                profileId = SELF_PROFILE_ID,
                embeddingBlob = SpeakerMath.toBlob(embedding),
            )
        )
        val samples = dao.samples(SELF_PROFILE_ID).takeLast(REQUIRED_SAMPLES)
        val prototype = SpeakerMath.averageNormalized(samples.map { SpeakerMath.fromBlob(it.embeddingBlob) })
        val count = samples.size
        dao.upsert(
            SpeakerProfileEntity(
                profileId = SELF_PROFILE_ID,
                profileType = "SELF",
                displayName = "我",
                modelId = SherpaSpeakerEngine.MODEL_ID,
                prototypeBlob = SpeakerMath.toBlob(prototype),
                sampleCount = count,
                threshold = policy.selfThreshold,
                enabled = count >= REQUIRED_SAMPLES,
                createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
            )
        )
        profileState(
            true,
            if (count >= REQUIRED_SAMPLES) "SELF声纹已建立" else "已采集${count}/${REQUIRED_SAMPLES}段SELF样本",
        )
    }

    suspend fun deleteSelfProfile(): SpeakerProfileState {
        db.speakerProfileDao().deleteSelf()
        return profileState(true, "SELF声纹已删除")
    }

    suspend fun classifyOnly(
        runtimeSessionKey: String,
        embedding: FloatArray,
        mode: SpeakerMode,
    ): SpeakerAnnotation {
        val normalized = SpeakerMath.normalize(embedding)
        val profile = db.speakerProfileDao().activeSelf()
        val similarity = profile?.let { SpeakerMath.cosine(SpeakerMath.fromBlob(it.prototypeBlob), normalized) } ?: Float.NaN
        val identity = policy.decide(profile != null, similarity)
        return when (identity) {
            SpeakerIdentity.SELF_CONFIRMED, SpeakerIdentity.SELF_PROBABLE -> SpeakerAnnotation("SELF", "SELF", similarity, identity)
            SpeakerIdentity.OTHER_CONFIRMED -> if (mode == SpeakerMode.SELF_AND_ABCD) {
                val assignment = clusterers.getOrPut(runtimeSessionKey) { OnlineSpeakerClusterer() }.assign(normalized)
                SpeakerAnnotation(assignment.clusterId, assignment.label, assignment.confidence, identity)
            } else SpeakerAnnotation("OTHER", "OTHER", (1f - similarity.coerceAtLeast(0f)), identity)
            SpeakerIdentity.UNKNOWN -> if (mode == SpeakerMode.SELF_AND_ABCD && profile == null) {
                val assignment = clusterers.getOrPut(runtimeSessionKey) { OnlineSpeakerClusterer() }.assign(normalized)
                SpeakerAnnotation(assignment.clusterId, assignment.label, assignment.confidence, SpeakerIdentity.OTHER_CONFIRMED)
            } else SpeakerAnnotation("UNKNOWN", "UNKNOWN", similarity.takeIf { !it.isNaN() } ?: 0f, identity)
        }
    }

    suspend fun annotate(
        sessionId: String,
        utteranceId: String,
        startMs: Long,
        embedding: FloatArray,
        mode: SpeakerMode,
    ): SpeakerAnnotation {
        val normalized = SpeakerMath.normalize(embedding)
        db.speakerEmbeddingDao().upsert(
            SpeakerEmbeddingEntity(
                embeddingId = "spemb_${UUID.randomUUID()}",
                utteranceId = utteranceId,
                sessionId = sessionId,
                modelId = SherpaSpeakerEngine.MODEL_ID,
                dimensions = normalized.size,
                vectorBlob = SpeakerMath.toBlob(normalized),
            )
        )

        val profile = db.speakerProfileDao().activeSelf()
        val similarity = profile?.let { SpeakerMath.cosine(SpeakerMath.fromBlob(it.prototypeBlob), normalized) } ?: Float.NaN
        val identity = policy.decide(profile != null, similarity)
        val annotation = when (identity) {
            SpeakerIdentity.SELF_CONFIRMED, SpeakerIdentity.SELF_PROBABLE -> SpeakerAnnotation("SELF", "SELF", similarity, identity)
            SpeakerIdentity.OTHER_CONFIRMED -> if (mode == SpeakerMode.SELF_AND_ABCD) {
                clusterAssignment(sessionId, normalized)
            } else {
                SpeakerAnnotation("OTHER", "OTHER", (1f - similarity.coerceAtLeast(0f)), identity)
            }
            SpeakerIdentity.UNKNOWN -> if (mode == SpeakerMode.SELF_AND_ABCD && profile == null) {
                clusterAssignment(sessionId, normalized)
            } else {
                SpeakerAnnotation("UNKNOWN", "UNKNOWN", similarity.takeIf { !it.isNaN() } ?: 0f, identity)
            }
        }
        db.utteranceDao().updateSpeaker(
            utteranceId = utteranceId,
            speakerId = annotation.speakerId,
            speakerLabel = annotation.speakerLabel,
            confidence = annotation.confidence,
            manual = false,
        )
        return annotation
    }

    private suspend fun clusterAssignment(sessionId: String, embedding: FloatArray): SpeakerAnnotation {
        val clusterer = clusterers[sessionId] ?: loadClusterer(sessionId).also { clusterers[sessionId] = it }
        val assignment = clusterer.assign(embedding)
        clusterer.snapshots().forEach { snapshot ->
            db.speakerClusterDao().upsert(
                SpeakerClusterEntity(
                    speakerId = snapshot.clusterId,
                    sessionId = sessionId,
                    speakerLabel = snapshot.label,
                    centroidBlob = SpeakerMath.toBlob(snapshot.centroid),
                    sampleCount = snapshot.sampleCount,
                    confidence = assignment.confidence,
                    manualLocked = snapshot.manualLocked,
                )
            )
        }
        return SpeakerAnnotation(assignment.clusterId, assignment.label, assignment.confidence, SpeakerIdentity.OTHER_CONFIRMED)
    }

    private suspend fun loadClusterer(sessionId: String): OnlineSpeakerClusterer {
        val snapshots = db.speakerClusterDao().allForSession(sessionId).map {
            SpeakerClusterSnapshot(
                clusterId = it.speakerId,
                label = it.speakerLabel,
                centroid = SpeakerMath.fromBlob(it.centroidBlob),
                sampleCount = it.sampleCount,
                manualLocked = it.manualLocked,
            )
        }
        return OnlineSpeakerClusterer(initial = snapshots)
    }

    suspend fun manualRelabel(utteranceId: String, label: String) {
        val speakerId = if (label == "SELF") "SELF" else "manual_${label}"
        db.utteranceDao().updateSpeaker(utteranceId, speakerId, label, 1f, true)
    }

    suspend fun clusterSummaries(sessionId: String): List<SpeakerClusterSummary> =
        db.speakerClusterDao().allForSession(sessionId).map {
            SpeakerClusterSummary(it.speakerId, it.speakerLabel, it.sampleCount, it.confidence, it.manualLocked)
        }

    suspend fun reclusterSession(sessionId: String): Map<String, String> {
        val utterances = db.utteranceDao().allForSession(sessionId)
        val byId = utterances.associateBy { it.utteranceId }
        val items = db.speakerEmbeddingDao().allForSession(sessionId)
            .filter { emb ->
                val u = byId[emb.utteranceId]
                u != null && !u.speakerManual && u.speakerLabel != "SELF"
            }
            .map { emb ->
                val u = byId.getValue(emb.utteranceId)
                SpeakerEmbeddingItem(emb.utteranceId, u.startMs, SpeakerMath.fromBlob(emb.vectorBlob))
            }
        val assignments = OnlineSpeakerClusterer.globalRecluster(items)
        db.speakerClusterDao().deleteAutomaticForSession(sessionId)
        val groups = assignments.entries.groupBy { it.value }
        groups.forEach { (label, entries) ->
            val embeddings = entries.mapNotNull { entry ->
                db.speakerEmbeddingDao().byUtterance(entry.key)?.let { SpeakerMath.fromBlob(it.vectorBlob) }
            }
            if (embeddings.isNotEmpty()) {
                val speakerId = "recluster_${sessionId}_$label"
                db.speakerClusterDao().upsert(
                    SpeakerClusterEntity(
                        speakerId = speakerId,
                        sessionId = sessionId,
                        speakerLabel = label,
                        centroidBlob = SpeakerMath.toBlob(SpeakerMath.averageNormalized(embeddings)),
                        sampleCount = embeddings.size,
                        confidence = 0.8f,
                    )
                )
                entries.forEach { entry ->
                    db.utteranceDao().updateSpeaker(entry.key, speakerId, label, 0.8f, false)
                }
            }
        }
        clusterers.remove(sessionId)
        return assignments
    }
}
