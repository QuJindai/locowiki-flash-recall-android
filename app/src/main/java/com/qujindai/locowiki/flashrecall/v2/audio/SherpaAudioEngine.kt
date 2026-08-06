package com.qujindai.locowiki.flashrecall.v2.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.k2fsa.sherpa.onnx.*
import com.qujindai.locowiki.flashrecall.v2.domain.AsrTiming
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class SherpaAudioEngine(
    private val context: Context,
    private val callback: Callback,
) : AutoCloseable {
    interface Callback {
        fun onModelState(ready: Boolean, message: String)
        fun onListeningState(listening: Boolean, vadSpeech: Boolean)
        fun onPartial(text: String)
        fun onFinal(text: String, timing: AsrTiming, samples: FloatArray)
        fun onError(message: String)
        fun onArchiveWarning(message: String) {}
        fun onAudioArchiveFinalized(path: String?) {}
    }

    companion object {
        const val SAMPLE_RATE = 16000
        const val MODEL_NAME = "sherpa-onnx-streaming-zipformer-small-bilingual-zh-en-2023-02-16"
        private const val MODEL_DIR = MODEL_NAME
        const val RING_BUFFER_SECONDS = 30
    }

    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private val running = AtomicBoolean(false)
    private val ring = PcmRingBuffer(SAMPLE_RATE * RING_BUFFER_SECONDS)
    @Volatile private var hotwords: String = ""
    @Volatile private var recognizer: OnlineRecognizer? = null
    @Volatile private var vad: Vad? = null
    @Volatile private var lastFinal: String = ""
    @Volatile private var lastSegment: FloatArray = FloatArray(0)
    @Volatile private var lastTiming: AsrTiming = AsrTiming()
    private var audioRecord: AudioRecord? = null
    @Volatile private var meetingRecorder: AacMeetingRecorder? = null
    @Volatile private var meetingAudioFile: File? = null

    fun initialize() {
        executor.execute {
            try {
                val modelConfig = OnlineModelConfig(
                    transducer = OnlineTransducerModelConfig(
                        encoder = "$MODEL_DIR/encoder-epoch-99-avg-1.int8.onnx",
                        decoder = "$MODEL_DIR/decoder-epoch-99-avg-1.onnx",
                        joiner = "$MODEL_DIR/joiner-epoch-99-avg-1.int8.onnx",
                    ),
                    tokens = "$MODEL_DIR/tokens.txt",
                    numThreads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4),
                    provider = "cpu",
                    modelType = "zipformer",
                    modelingUnit = "cjkchar",
                )
                val config = OnlineRecognizerConfig(
                    featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
                    modelConfig = modelConfig,
                    endpointConfig = EndpointConfig(
                        rule1 = EndpointRule(false, 2.0f, 0f),
                        rule2 = EndpointRule(true, 0.75f, 0f),
                        rule3 = EndpointRule(false, 0f, 15f),
                    ),
                    enableEndpoint = true,
                    decodingMethod = "modified_beam_search",
                    maxActivePaths = 4,
                    hotwordsScore = 2.0f,
                )
                recognizer = OnlineRecognizer(context.assets, config)
                vad = Vad(
                    context.assets,
                    VadModelConfig(
                        sileroVadModelConfig = SileroVadModelConfig(
                            model = "silero_vad.onnx",
                            threshold = 0.5f,
                            minSilenceDuration = 0.35f,
                            minSpeechDuration = 0.18f,
                            windowSize = 512,
                            maxSpeechDuration = 12f,
                        ),
                        sampleRate = SAMPLE_RATE,
                        numThreads = 1,
                        provider = "cpu",
                    ),
                )
                post { callback.onModelState(true, "Sherpa-ONNX 双语流式模型已就绪") }
            } catch (t: Throwable) {
                post { callback.onModelState(false, "模型初始化失败：${t.message ?: t.javaClass.simpleName}") }
            }
        }
    }

    fun updateHotwords(value: String) { hotwords = value }

    fun start(audioFile: File? = null): Boolean {
        if (running.get()) return true
        if (recognizer == null || vad == null) {
            callback.onError("本地语音模型尚未就绪")
            return false
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            callback.onError("没有麦克风权限")
            return false
        }
        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuffer <= 0) {
            callback.onError("无法获得麦克风缓冲区")
            return false
        }
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBuffer * 2,
        )
        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord?.release(); audioRecord = null
            callback.onError("麦克风初始化失败")
            return false
        }
        ring.clear()
        lastFinal = ""
        meetingAudioFile = null
        meetingRecorder = audioFile?.let { file ->
            runCatching { AacMeetingRecorder(file, SAMPLE_RATE) }
                .onSuccess { meetingAudioFile = file }
                .onFailure { post { callback.onArchiveWarning("完整音频启动失败，已继续保存文字：${it.message ?: it.javaClass.simpleName}") } }
                .getOrNull()
        }
        lastSegment = FloatArray(0)
        running.set(true)
        audioRecord?.startRecording()
        executor.execute(::recordLoop)
        callback.onListeningState(true, false)
        return true
    }

    fun stop() {
        if (!running.getAndSet(false)) return
        runCatching { audioRecord?.stop() }
        audioRecord?.release()
        audioRecord = null
        post { callback.onListeningState(false, false) }
    }

    fun latestQuestion(): Pair<String, AsrTiming> {
        val text = lastFinal.trim()
        if (text.isNotEmpty()) return text to lastTiming
        val segment = lastSegment
        if (segment.isEmpty()) return "" to lastTiming
        val recognized = recognizeSegment(segment)
        if (recognized.isNotBlank()) lastFinal = recognized
        return recognized to lastTiming
    }

    fun recentAudioSnapshot(): FloatArray = ring.snapshot()

    fun latestSpeechSegment(): FloatArray = lastSegment.copyOf()

    private fun recordLoop() {
        val localRecognizer = recognizer ?: return
        val localVad = vad ?: return
        val stream = localRecognizer.createStream(hotwords)
        val shortBuffer = ShortArray(1600)
        var previousText = ""
        var speechStartNs = 0L
        var speechEndNs = 0L
        var firstPartialNs = 0L
        var vadSpeech = false
        try {
            while (running.get()) {
                val count = audioRecord?.read(shortBuffer, 0, shortBuffer.size, AudioRecord.READ_BLOCKING) ?: break
                if (count <= 0) continue
                val samples = FloatArray(count) { shortBuffer[it] / 32768f }
                ring.write(samples)
                meetingRecorder?.let { recorder ->
                    runCatching { recorder.write(shortBuffer, count) }
                        .onFailure {
                            runCatching { recorder.close() }
                            meetingRecorder = null
                            meetingAudioFile = null
                            post { callback.onArchiveWarning("完整音频写入失败，已继续保存文字：${it.message ?: it.javaClass.simpleName}") }
                        }
                }
                localVad.acceptWaveform(samples)
                val now = System.nanoTime()
                val currentVad = localVad.isSpeechDetected()
                if (currentVad && !vadSpeech) {
                    speechStartNs = now
                    speechEndNs = 0L
                    firstPartialNs = 0L
                }
                if (!currentVad && vadSpeech) speechEndNs = now
                if (currentVad != vadSpeech) {
                    vadSpeech = currentVad
                    post { callback.onListeningState(true, currentVad) }
                }
                while (!localVad.empty()) {
                    val segment = localVad.front()
                    lastSegment = segment.samples.copyOf()
                    localVad.pop()
                }

                stream.acceptWaveform(samples, SAMPLE_RATE)
                while (localRecognizer.isReady(stream)) localRecognizer.decode(stream)
                val text = localRecognizer.getResult(stream).text.trim()
                if (text.isNotBlank() && text != previousText) {
                    if (firstPartialNs == 0L) firstPartialNs = now
                    previousText = text
                    post { callback.onPartial(text) }
                }
                if (localRecognizer.isEndpoint(stream)) {
                    val finalText = text
                    val finalNs = System.nanoTime()
                    if (speechEndNs == 0L) speechEndNs = finalNs
                    val timing = AsrTiming(speechStartNs, speechEndNs, firstPartialNs, finalNs)
                    if (finalText.isNotBlank()) {
                        lastFinal = finalText
                        lastTiming = timing
                        val speakerSamples = lastSegment.copyOf()
                        post { callback.onFinal(finalText, timing, speakerSamples) }
                    }
                    localRecognizer.reset(stream)
                    previousText = ""
                    speechStartNs = 0L
                    speechEndNs = 0L
                    firstPartialNs = 0L
                }
            }
        } catch (t: Throwable) {
            post { callback.onError("语音处理失败：${t.message ?: t.javaClass.simpleName}") }
        } finally {
            runCatching { localVad.flush() }
            while (!localVad.empty()) {
                lastSegment = localVad.front().samples.copyOf()
                localVad.pop()
            }
            stream.release()
            val finalizedPath = meetingAudioFile?.let { file ->
                runCatching { meetingRecorder?.close() }
                meetingRecorder = null
                meetingAudioFile = null
                file.absolutePath.takeIf { file.isFile && file.length() > 0L }
            }
            post { callback.onAudioArchiveFinalized(finalizedPath) }
        }
    }

    private fun recognizeSegment(samples: FloatArray): String {
        val localRecognizer = recognizer ?: return ""
        return runCatching {
            val stream = localRecognizer.createStream(hotwords)
            stream.acceptWaveform(samples, SAMPLE_RATE)
            stream.acceptWaveform(FloatArray((0.8f * SAMPLE_RATE).toInt()), SAMPLE_RATE)
            stream.inputFinished()
            while (localRecognizer.isReady(stream)) localRecognizer.decode(stream)
            val text = localRecognizer.getResult(stream).text.trim()
            stream.release()
            text
        }.getOrDefault("")
    }

    private fun post(block: () -> Unit) = handler.post { block() }

    override fun close() {
        stop()
        executor.shutdownNow()
        recognizer?.release(); recognizer = null
        vad?.release(); vad = null
        ring.clear()
        lastSegment = FloatArray(0)
        lastFinal = ""
    }
}
