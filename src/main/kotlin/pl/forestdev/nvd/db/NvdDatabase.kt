package pl.forestdev.nvd.db

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.batchUpsert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert
import org.jetbrains.exposed.v1.json.jsonb
import java.sql.DriverManager

val nvdJson = Json { ignoreUnknownKeys = true; encodeDefaults = false }

object Cves : Table("cves") {
    val id = varchar("id", 32)
    val lastModified = varchar("last_modified", 32).index()
    val data = jsonb("data", nvdJson, JsonObject.serializer())
    override val primaryKey = PrimaryKey(id)
}

object SyncState : Table("sync_state") {
    val key = varchar("key", 64)
    val value = varchar("value", 32)
    override val primaryKey = PrimaryKey(key)
}

object DatabaseFactory {
    fun connect(url: String): Database {
        applySqlitePragmas(url)
        return Database.connect(url, driver = "org.sqlite.JDBC")
    }

    private fun applySqlitePragmas(url: String) {
        if (url == "jdbc:sqlite::memory:") return
        DriverManager.getConnection(url).use { conn ->
            conn.autoCommit = true
            conn.createStatement().use { st ->
                st.execute("PRAGMA journal_mode=WAL;")
                st.execute("PRAGMA busy_timeout=5000;")
                st.execute("PRAGMA synchronous=NORMAL;")
            }
        }
    }

    fun initSchema(db: Database) = transaction(db) {
        SchemaUtils.create(Cves, SyncState)
    }
}

class CveRepository(private val db: Database) {
    companion object {
        const val CHECKPOINT_KEY = "cve.lastModEndDate"
    }

    fun loadCheckpoint(): String? = transaction(db) {
        SyncState.selectAll().where { SyncState.key eq CHECKPOINT_KEY }
            .singleOrNull()?.get(SyncState.value)
    }

    fun saveCheckpoint(value: String) = transaction(db) {
        SyncState.upsert {
            it[key] = CHECKPOINT_KEY
            it[SyncState.value] = value
        }
    }

    fun persistPage(vulns: List<JsonObject>) {
        val rows = vulns.mapNotNull(::extractCve)
        if (rows.isEmpty()) return
        transaction(db) {
            Cves.batchUpsert(rows) { (id, lastMod, cve) ->
                this[Cves.id] = id
                this[Cves.lastModified] = lastMod
                this[Cves.data] = cve
            }
        }
    }

    private fun extractCve(vuln: JsonObject): Triple<String, String, JsonObject>? {
        val cve = vuln["cve"] as? JsonObject ?: return null
        val id = (cve["id"] as? JsonPrimitive)?.content ?: return null
        val lastMod = (cve["lastModified"] as? JsonPrimitive)?.content ?: ""
        return Triple(id, lastMod, cve)
    }
}
