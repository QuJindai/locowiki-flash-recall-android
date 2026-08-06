package com.qujindai.locowiki.flashrecall.v2.speaker

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SelfEnrollmentSerialExecutorTest {
    @Test fun `overlapping saves run one at a time`() = runTest {
        val serial = SelfEnrollmentSerialExecutor()
        val active = AtomicInteger()
        val maximum = AtomicInteger()
        List(3) {
            async {
                serial.run {
                    val now = active.incrementAndGet()
                    maximum.updateAndGet { old -> maxOf(old, now) }
                    delay(20)
                    active.decrementAndGet()
                }
            }
        }.awaitAll()
        assertEquals(1, maximum.get())
    }
}
