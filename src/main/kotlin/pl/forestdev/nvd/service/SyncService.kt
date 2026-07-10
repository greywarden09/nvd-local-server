package pl.forestdev.nvd.service

import io.ktor.client.HttpClient
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.v1.jdbc.Database
import org.slf4j.Logger
import pl.forestdev.nvd.client.NvdConfig
import pl.forestdev.nvd.client.RateLimiter
import pl.forestdev.nvd.client.fetchCvePage
import pl.forestdev.nvd.db.CveRepository
import pl.forestdev.nvd.util.NvdTime

private data class SyncPlan(
    val target: String,
    val windows: List<Pair<String?, String?>>,
    val firstIndex: Int,
)

class SyncService(
    private val client: HttpClient,
    private val limiter: RateLimiter,
    private val config: NvdConfig,
    private val db: Database,
    private val repository: CveRepository,
    private val schedulerService: SyncSchedulerService,
    private val log: Logger,
) {
    suspend fun runSync(): Int {
        val resume = schedulerService.loadResumeState()

        val plan: SyncPlan = if (resume != null) {
            if (resume.from == null) {
                log.info("NVD: resuming full backfill from startIndex={}", resume.index)
                SyncPlan(resume.target, listOf(null to null), resume.index)
            } else {
                log.info("NVD: resuming window {} .. {} from startIndex={}", resume.from, resume.to, resume.index)
                val rest = NvdTime.windows(resume.to!!, resume.target)
                SyncPlan(resume.target, listOf<Pair<String?, String?>>(resume.from to resume.to) + rest, resume.index)
            }
        } else {
            val target = NvdTime.now()
            val checkpoint = repository.loadCheckpoint()
            if (checkpoint == null) {
                log.info("NVD: full backfill")
                SyncPlan(target, listOf(null to null), 0)
            } else {
                val ws = NvdTime.windows(checkpoint, target)
                log.info("NVD: incremental, {} window(s) since {}", ws.size, checkpoint)
                SyncPlan(target, ws, 0)
            }
        }

        var total = 0
        plan.windows.forEachIndexed { i, (from, to) ->
            val startFrom = if (i == 0) plan.firstIndex else 0
            schedulerService.saveResumeWindow(plan.target, from, to, startFrom)
            val n = pagedFetch(from, to, startFrom) { repository.persistPage(it) }
            if (from != null) log.info("  window {} .. {} → {} records", from, to, n)
            total += n
        }

        repository.saveCheckpoint(plan.target)
        schedulerService.clearResumeState()
        return total
    }

    private suspend fun pagedFetch(
        lastModStart: String?, lastModEnd: String?,
        resumeFrom: Int,
        persist: suspend (List<JsonObject>) -> Unit,
    ): Int {
        var startIndex = resumeFrom
        var count = 0
        while (true) {
            val page = client.fetchCvePage(limiter, startIndex, config.resultsPerPage, lastModStart, lastModEnd)
            if (page.vulnerabilities.isNotEmpty()) {
                persist(page.vulnerabilities)
                count += page.vulnerabilities.size
            }
            startIndex += page.resultsPerPage
            schedulerService.saveResumeIndex(startIndex)
            if (startIndex >= page.totalResults) break
        }
        return count
    }
}
