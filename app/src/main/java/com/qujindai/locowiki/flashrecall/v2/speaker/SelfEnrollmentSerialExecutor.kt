package com.qujindai.locowiki.flashrecall.v2.speaker

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class SelfEnrollmentSerialExecutor {
    private val mutex = Mutex()
    suspend fun <T> run(block: suspend () -> T): T = mutex.withLock { block() }
}
