package com.qujindai.locowiki.flashrecall.v2.speaker

import android.content.Context
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractor
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig

class SherpaSpeakerEngine(private val context: Context) : AutoCloseable {
    companion object {
        const val MODEL_ID = "3dspeaker-campplus-zh-cn-16k-common"
        const val MODEL_ASSET = "speaker/3dspeaker_speech_campplus_sv_zh-cn_16k-common.onnx"
        const val SAMPLE_RATE = 16000
        private const val MIN_SAMPLES = SAMPLE_RATE
        private const val MAX_SAMPLES = SAMPLE_RATE * 15
    }

    @Volatile private var extractor: SpeakerEmbeddingExtractor? = null

    fun initialize(): Result<Int> = runCatching {
        synchronized(this) {
            if (extractor == null) {
                extractor = SpeakerEmbeddingExtractor(
                    assetManager = context.assets,
                    config = SpeakerEmbeddingExtractorConfig(
                        model = MODEL_ASSET,
                        numThreads = Runtime.getRuntime().availableProcessors().coerceIn(1, 2),
                        debug = false,
                        provider = "cpu",
                    ),
                )
            }
            extractor!!.dim()
        }
    }

    fun isReady(): Boolean = extractor != null

    fun compute(samples: FloatArray): FloatArray? {
        if (samples.size < MIN_SAMPLES) return null
        val local = extractor ?: return null
        val trimmed = if (samples.size > MAX_SAMPLES) samples.copyOfRange(samples.size - MAX_SAMPLES, samples.size) else samples.copyOf()
        return synchronized(this) {
            runCatching {
                val stream = local.createStream()
                try {
                    stream.acceptWaveform(sampleRate = SAMPLE_RATE, samples = trimmed)
                    stream.inputFinished()
                    if (!local.isReady(stream)) return@synchronized null
                    val result = local.compute(stream)
                    SpeakerMath.normalize(result)
                } finally {
                    stream.release()
                }
            }.getOrNull()
        }
    }

    override fun close() {
        synchronized(this) {
            extractor?.release()
            extractor = null
        }
    }
}
