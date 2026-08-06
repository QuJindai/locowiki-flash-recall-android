package com.qujindai.locowiki.flashrecall.v2.speaker

import org.junit.Assert.*
import org.junit.Test

class OnlineSpeakerClustererTest {
    @Test fun similarEmbeddingsStayInSameCluster() {
        val clusterer = OnlineSpeakerClusterer(mergeThreshold = 0.80f, maxClusters = 4)
        val first = clusterer.assign(floatArrayOf(1f, 0f))
        val second = clusterer.assign(floatArrayOf(0.99f, 0.01f))
        assertEquals("A", first.label)
        assertEquals(first.clusterId, second.clusterId)
    }

    @Test fun dissimilarEmbeddingsCreateNewCluster() {
        val clusterer = OnlineSpeakerClusterer(mergeThreshold = 0.80f, maxClusters = 4)
        val first = clusterer.assign(floatArrayOf(1f, 0f))
        val second = clusterer.assign(floatArrayOf(0f, 1f))
        assertNotEquals(first.clusterId, second.clusterId)
        assertEquals("B", second.label)
    }

    @Test fun maxClustersFallsBackToBestExistingCluster() {
        val clusterer = OnlineSpeakerClusterer(mergeThreshold = 0.95f, maxClusters = 2)
        clusterer.assign(floatArrayOf(1f, 0f))
        clusterer.assign(floatArrayOf(0f, 1f))
        val third = clusterer.assign(floatArrayOf(-1f, 0f))
        assertEquals(2, clusterer.snapshots().size)
        assertTrue(third.label in setOf("A", "B"))
    }

    @Test fun globalReclusterIsDeterministic() {
        val items = listOf(
            SpeakerEmbeddingItem("u1", 1L, floatArrayOf(1f, 0f)),
            SpeakerEmbeddingItem("u2", 2L, floatArrayOf(0.98f, 0.02f)),
            SpeakerEmbeddingItem("u3", 3L, floatArrayOf(0f, 1f)),
        )
        val result = OnlineSpeakerClusterer.globalRecluster(items, 0.8f, 4)
        assertEquals(result["u1"], result["u2"])
        assertNotEquals(result["u1"], result["u3"])
    }
}
