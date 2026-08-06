package com.qujindai.locowiki.flashrecall.v2.audio

class PcmRingBuffer(private val capacity: Int) {
    init { require(capacity > 0) }
    private val data = FloatArray(capacity)
    private var writeIndex = 0
    private var size = 0

    @Synchronized
    fun write(samples: FloatArray) {
        samples.forEach { sample ->
            data[writeIndex] = sample
            writeIndex = (writeIndex + 1) % capacity
            if (size < capacity) size++
        }
    }

    @Synchronized
    fun snapshot(): FloatArray {
        val out = FloatArray(size)
        val start = (writeIndex - size + capacity) % capacity
        for (i in 0 until size) out[i] = data[(start + i) % capacity]
        return out
    }

    @Synchronized
    fun clear() {
        writeIndex = 0
        size = 0
        data.fill(0f)
    }

    @Synchronized
    fun sampleCount(): Int = size
}
