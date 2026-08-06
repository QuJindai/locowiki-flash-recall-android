package com.qujindai.locowiki.flashrecall.v2.audio

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteOrder

class AacMeetingRecorder(
    outputFile: File,
    private val sampleRate: Int = 16_000,
    bitRate: Int = 32_000,
) : AutoCloseable {
    private val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
    private val muxer: MediaMuxer
    private val bufferInfo = MediaCodec.BufferInfo()
    private var trackIndex = -1
    private var muxerStarted = false
    private var totalSamples = 0L
    private var closed = false

    init {
        outputFile.parentFile?.mkdirs()
        if (outputFile.exists()) outputFile.delete()
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 8192)
        }
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        codec.start()
    }

    @Synchronized
    fun write(samples: ShortArray, count: Int) {
        check(!closed)
        var offset = 0
        val boundedCount = count.coerceIn(0, samples.size)
        while (offset < boundedCount) {
            val inputIndex = codec.dequeueInputBuffer(10_000)
            if (inputIndex < 0) {
                drain(false)
                continue
            }
            val input = codec.getInputBuffer(inputIndex) ?: continue
            input.clear()
            val frames = minOf((input.capacity() / 2), boundedCount - offset)
            input.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(samples, offset, frames)
            val presentationUs = totalSamples * 1_000_000L / sampleRate
            codec.queueInputBuffer(inputIndex, 0, frames * 2, presentationUs, 0)
            totalSamples += frames
            offset += frames
            drain(false)
        }
    }

    private fun queueEndOfStream() {
        repeat(50) {
            val inputIndex = codec.dequeueInputBuffer(10_000)
            if (inputIndex >= 0) {
                val presentationUs = totalSamples * 1_000_000L / sampleRate
                codec.queueInputBuffer(inputIndex, 0, 0, presentationUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                return
            }
            drain(false)
        }
    }

    private fun drain(endOfStream: Boolean) {
        var idleCount = 0
        while (true) {
            val outputIndex = codec.dequeueOutputBuffer(bufferInfo, if (endOfStream) 10_000 else 0)
            when {
                outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!endOfStream || idleCount++ > 50) return
                }
                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (muxerStarted) error("AAC输出格式重复变化")
                    trackIndex = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                    muxerStarted = true
                }
                outputIndex >= 0 -> {
                    val output = codec.getOutputBuffer(outputIndex)
                    if (output != null && bufferInfo.size > 0 && muxerStarted && bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                        output.position(bufferInfo.offset)
                        output.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(trackIndex, output, bufferInfo)
                    }
                    val eos = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    codec.releaseOutputBuffer(outputIndex, false)
                    if (eos) return
                }
            }
        }
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        runCatching {
            queueEndOfStream()
            drain(true)
        }
        runCatching { codec.stop() }
        codec.release()
        if (muxerStarted) runCatching { muxer.stop() }
        muxer.release()
    }
}
