package com.qujindai.locowiki.flashrecall.v2.audio

internal interface SegmentCaptureSession : AutoCloseable {
    fun start(): Boolean
    fun stopAndTakeSamples(): FloatArray
}

internal class SegmentRecorderController(
    private val factory: (Int) -> SegmentCaptureSession,
) : AutoCloseable {
    private val lock = Any()
    private var active: SegmentCaptureSession? = null

    fun start(segmentNumber: Int): Boolean = synchronized(lock) {
        if (active != null) return false
        require(segmentNumber > 0) { "segmentNumber must be positive" }
        val session = factory(segmentNumber)
        if (!session.start()) {
            session.close()
            return false
        }
        active = session
        true
    }

    fun stopAndTakeSamples(): FloatArray {
        val session = synchronized(lock) {
            val current = active ?: return FloatArray(0)
            active = null
            current
        }
        return try {
            session.stopAndTakeSamples()
        } finally {
            session.close()
        }
    }

    override fun close() {
        val session = synchronized(lock) {
            val current = active
            active = null
            current
        }
        session?.close()
    }
}
