package pl.forestdev.nvd.service

import kotlinx.coroutines.*
import org.slf4j.Logger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class SyncScheduler(
    private val syncService: SyncService,
    private val log: Logger,
    private val interval: Duration = 2.hours,
    private val initialBackoff: Duration = 30.seconds,
    private val maxBackoff: Duration = 5.minutes,
) {
    fun CoroutineScope.schedule(onSyncComplete: (suspend () -> Unit)? = null): Job = launch {
        var backoff = initialBackoff
        while (isActive) {
            val nextDelay = try {
                val n = syncService.runSync()
                log.info("NVD sync OK: {} records in total", n)
                onSyncComplete?.invoke()
                backoff = initialBackoff
                interval
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val wait = backoff
                log.warn("NVD sync error: {} — retrying in {}", e.message, wait)
                backoff = (backoff * 2).coerceAtMost(maxBackoff)
                wait
            }
            delay(nextDelay)
        }
    }
}
