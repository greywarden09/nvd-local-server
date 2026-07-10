package pl.forestdev.nvd.client

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.milliseconds

class RateLimiter(private val minIntervalMs: Long) {
    private val mutex = Mutex()
    private var lastStart = 0L

    suspend fun <T> withPermit(block: suspend () -> T): T = mutex.withLock {
        val now = System.currentTimeMillis()
        val start = maxOf(now, lastStart + minIntervalMs)
        val wait = start - now
        if (wait > 0) delay(wait.milliseconds)
        lastStart = start
        block()
    }
}
