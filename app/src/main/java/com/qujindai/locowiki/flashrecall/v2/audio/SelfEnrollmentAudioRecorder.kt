package com.qujindai.locowiki.flashrecall.v2.audio

import android.Manifest
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.qujindai.locowiki.flashrecall.v2.BuildConfig
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class SelfEnrollmentAudioRecorder(private val context: Context) : AutoCloseable {
    private val controller = SegmentRecorderController(::createSession)

    fun start(segmentNumber: Int): Boolean = controller.start(segmentNumber)
    fun stopAndTakeSamples(): FloatArray = controller.stopAndTakeSamples()
    override fun close() = controller.close()

    private fun createSession(segmentNumber: Int): SegmentCaptureSession {
        val isDebuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (BuildConfig.VOICEPRINT_QA_PCM_ALLOWED && isDebuggable) {
            val qaDirectory = File(context.filesDir, QA_DIRECTORY)
            if (File(qaDirectory, QA_ENABLED_FILE).isFile) {
                return Pcm16FileCaptureSession(File(qaDirectory, "segment-$segmentNumber.pcm"))
            }
        }
        return AndroidSegmentCaptureSession(context)
    }

    private class AndroidSegmentCaptureSession(private val context: Context) : SegmentCaptureSession {
        private val running = AtomicBoolean(false)
        private val ring = PcmRingBuffer(SAMPLE_RATE * MAX_SECONDS)
        private var recorder: AudioRecord? = null
        private var readerThread: Thread? = null

        override fun start(): Boolean {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return false
            val minBuffer = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            if (minBuffer <= 0) return false
            val local = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuffer * 2,
            )
            if (local.state != AudioRecord.STATE_INITIALIZED) {
                local.release()
                return false
            }
            return runCatching {
                ring.clear()
                local.startRecording()
                check(local.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "AudioRecord did not enter recording state" }
                recorder = local
                running.set(true)
                readerThread = Thread({ readLoop(local) }, "self-enrollment-audio").apply {
                    isDaemon = true
                    start()
                }
                true
            }.getOrElse {
                running.set(false)
                runCatching { local.stop() }
                local.release()
                recorder = null
                false
            }
        }

        private fun readLoop(local: AudioRecord) {
            val buffer = ShortArray(1600)
            while (running.get()) {
                val count = local.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                if (count > 0) {
                    ring.write(FloatArray(count) { buffer[it] / 32768f })
                } else if (count == AudioRecord.ERROR_DEAD_OBJECT || count == AudioRecord.ERROR_INVALID_OPERATION) {
                    break
                }
            }
        }

        override fun stopAndTakeSamples(): FloatArray {
            stopAndJoin()
            return ring.snapshot()
        }

        private fun stopAndJoin() {
            val wasRunning = running.getAndSet(false)
            val local = recorder
            if (wasRunning) runCatching { local?.stop() }
            runCatching { readerThread?.join(JOIN_TIMEOUT_MS) }
            readerThread = null
        }

        override fun close() {
            stopAndJoin()
            recorder?.release()
            recorder = null
        }

        companion object {
            private const val SAMPLE_RATE = 16000
            private const val MAX_SECONDS = 12
            private const val JOIN_TIMEOUT_MS = 2000L
        }
    }

    companion object {
        private const val QA_DIRECTORY = "voiceprint-qa"
        private const val QA_ENABLED_FILE = "enabled"
    }
}
