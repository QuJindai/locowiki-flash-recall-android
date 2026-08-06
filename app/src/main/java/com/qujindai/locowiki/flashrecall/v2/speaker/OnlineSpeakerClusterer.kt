package com.qujindai.locowiki.flashrecall.v2.speaker

import java.util.UUID

data class SpeakerClusterSnapshot(
    val clusterId: String,
    val label: String,
    val centroid: FloatArray,
    val sampleCount: Int,
    val manualLocked: Boolean = false,
)

data class SpeakerAssignment(
    val clusterId: String,
    val label: String,
    val confidence: Float,
    val isNew: Boolean,
)

data class SpeakerEmbeddingItem(
    val utteranceId: String,
    val startMs: Long,
    val embedding: FloatArray,
)

class OnlineSpeakerClusterer(
    private val mergeThreshold: Float = 0.68f,
    private val maxClusters: Int = 8,
    initial: List<SpeakerClusterSnapshot> = emptyList(),
) {
    private val clusters = initial.map { it.copy(centroid = it.centroid.copyOf()) }.toMutableList()

    fun assign(embedding: FloatArray): SpeakerAssignment {
        val normalized = SpeakerMath.normalize(embedding)
        require(normalized.isNotEmpty())
        val best = clusters
            .map { it to SpeakerMath.cosine(it.centroid, normalized) }
            .maxByOrNull { it.second }
        if (best != null && (best.second >= mergeThreshold || clusters.size >= maxClusters)) {
            val index = clusters.indexOfFirst { it.clusterId == best.first.clusterId }
            val old = clusters[index]
            val count = old.sampleCount.coerceAtLeast(1)
            val merged = FloatArray(normalized.size) { i ->
                (old.centroid[i] * count + normalized[i]) / (count + 1)
            }
            clusters[index] = old.copy(centroid = SpeakerMath.normalize(merged), sampleCount = count + 1)
            return SpeakerAssignment(old.clusterId, old.label, best.second, false)
        }
        val label = labelFor(clusters.size)
        val created = SpeakerClusterSnapshot(
            clusterId = "spk_${UUID.randomUUID()}",
            label = label,
            centroid = normalized,
            sampleCount = 1,
        )
        clusters += created
        return SpeakerAssignment(created.clusterId, created.label, 1f, true)
    }

    fun snapshots(): List<SpeakerClusterSnapshot> = clusters.map { it.copy(centroid = it.centroid.copyOf()) }

    fun replaceLabel(clusterId: String, label: String, manualLocked: Boolean = true) {
        val index = clusters.indexOfFirst { it.clusterId == clusterId }
        if (index >= 0) clusters[index] = clusters[index].copy(label = label, manualLocked = manualLocked)
    }

    companion object {
        fun labelFor(index: Int): String = if (index in 0..25) ('A'.code + index).toChar().toString() else "S${index + 1}"

        fun globalRecluster(
            items: List<SpeakerEmbeddingItem>,
            mergeThreshold: Float = 0.68f,
            maxClusters: Int = 8,
        ): Map<String, String> {
            val clusterer = OnlineSpeakerClusterer(mergeThreshold, maxClusters)
            val result = linkedMapOf<String, String>()
            items.sortedBy { it.startMs }.forEach { item ->
                result[item.utteranceId] = clusterer.assign(item.embedding).label
            }
            return result
        }
    }
}
