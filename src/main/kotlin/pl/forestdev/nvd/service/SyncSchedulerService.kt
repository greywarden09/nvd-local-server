package pl.forestdev.nvd.service

import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert
import pl.forestdev.nvd.db.SyncState

data class ResumeState(val target: String, val from: String?, val to: String?, val index: Int)

class SyncSchedulerService(private val db: Database) {
    companion object {
        private const val RESUME_TARGET_KEY = "cve.resumeTarget"
        private const val RESUME_FROM_KEY = "cve.resumeFrom"
        private const val RESUME_TO_KEY = "cve.resumeTo"
        private const val RESUME_INDEX_KEY = "cve.resumeIndex"
        private val RESUME_KEYS = listOf(RESUME_TARGET_KEY, RESUME_FROM_KEY, RESUME_TO_KEY, RESUME_INDEX_KEY)
    }

    fun saveResumeWindow(target: String, from: String?, to: String?, index: Int) =
        transaction(db) {
            putState(RESUME_TARGET_KEY, target)
            putState(RESUME_FROM_KEY, from ?: "")
            putState(RESUME_TO_KEY, to ?: "")
            putState(RESUME_INDEX_KEY, index.toString())
        }

    fun saveResumeIndex(index: Int) = transaction(db) {
        putState(RESUME_INDEX_KEY, index.toString())
    }

    fun loadResumeState(): ResumeState? = transaction(db) {
        val rows = SyncState.selectAll()
            .where { SyncState.key inList RESUME_KEYS }
            .associate { it[SyncState.key] to it[SyncState.value] }
        val target = rows[RESUME_TARGET_KEY] ?: return@transaction null
        ResumeState(
            target = target,
            from = rows[RESUME_FROM_KEY]?.ifEmpty { null },
            to = rows[RESUME_TO_KEY]?.ifEmpty { null },
            index = rows[RESUME_INDEX_KEY]?.toIntOrNull() ?: 0,
        )
    }

    fun clearResumeState() = transaction(db) {
        SyncState.deleteWhere { SyncState.key inList RESUME_KEYS }
    }

    private fun putState(k: String, v: String) {
        SyncState.upsert {
            it[key] = k
            it[value] = v
        }
    }
}
