package com.qujindai.locowiki.flashrecall.v2.speaker

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

object SpeakerMath {
    fun cosine(a: FloatArray, b: FloatArray): Float {
        if (a.isEmpty() || a.size != b.size) return -1f
        var dot = 0.0
        var na = 0.0
        var nb = 0.0
        for (i in a.indices) {
            val av = a[i].toDouble()
            val bv = b[i].toDouble()
            dot += av * bv
            na += av * av
            nb += bv * bv
        }
        if (na <= 0.0 || nb <= 0.0) return -1f
        return (dot / sqrt(na * nb)).toFloat().coerceIn(-1f, 1f)
    }

    fun normalize(value: FloatArray): FloatArray {
        if (value.isEmpty()) return value
        var sum = 0.0
        value.forEach { sum += it.toDouble() * it.toDouble() }
        if (sum <= 0.0) return FloatArray(value.size)
        val norm = sqrt(sum).toFloat()
        return FloatArray(value.size) { value[it] / norm }
    }

    fun averageNormalized(values: List<FloatArray>): FloatArray {
        val valid = values.filter { it.isNotEmpty() }
        if (valid.isEmpty()) return FloatArray(0)
        val size = valid.first().size
        require(valid.all { it.size == size }) { "embedding dimensions must match" }
        val sum = FloatArray(size)
        valid.forEach { value ->
            val normalized = normalize(value)
            for (i in 0 until size) sum[i] += normalized[i]
        }
        return normalize(sum)
    }

    fun toBlob(value: FloatArray): ByteArray = ByteBuffer
        .allocate(value.size * 4)
        .order(ByteOrder.LITTLE_ENDIAN)
        .also { buffer -> value.forEach(buffer::putFloat) }
        .array()

    fun fromBlob(blob: ByteArray): FloatArray {
        require(blob.size % 4 == 0) { "invalid float blob" }
        val buffer = ByteBuffer.wrap(blob).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(blob.size / 4) { buffer.float }
    }
}
