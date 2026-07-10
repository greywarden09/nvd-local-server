package pl.forestdev.nvd.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import pl.forestdev.nvd.db.Cves
import pl.forestdev.nvd.db.nvdJson
import pl.forestdev.nvd.util.NvdTime
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.time.Year
import java.util.zip.GZIPOutputStream
import pl.forestdev.nvd.client.NvdCveResponse

class DatafeedGenerator(
    private val db: Database,
    private val outDir: File,
    private val prefix: String = "nvdcve-",
) {
    fun generate(currentYear: Int = Year.now().value) {
        outDir.mkdirs()

        for (year in 2002..currentYear) {
            val pred: Op<Boolean> =
                if (year == 2002) Cves.id less "CVE-2003-"
                else (Cves.id greaterEq "CVE-$year-") and (Cves.id less "CVE-${year + 1}-")
            writeFeed("$prefix$year", collect(pred))
        }

        val cutoff = NvdTime.format(Instant.now().minus(Duration.ofDays(8)))
        writeFeed("${prefix}modified", collect(Cves.lastModified greaterEq cutoff))

        File(outDir, "cache.properties").writeText(
            "lastModifiedDate=${NvdTime.iso()}\nprefix=$prefix\n"
        )
    }

    suspend fun generateAsync(currentYear: Int = Year.now().value) =
        withContext(Dispatchers.IO) { generate(currentYear) }

    private fun collect(pred: Op<Boolean>): List<JsonObject> =
        transaction(db) {
            Cves.selectAll().where(pred)
                .orderBy(Cves.id to SortOrder.ASC)
                .map { JsonObject(mapOf("cve" to it[Cves.data])) }
        }

    private fun writeFeed(name: String, vulns: List<JsonObject>) {
        val envelope = NvdCveResponse(
            resultsPerPage = vulns.size,
            startIndex = 0,
            totalResults = vulns.size,
            timestamp = NvdTime.now(),
            vulnerabilities = vulns,
        )
        val json = nvdJson.encodeToString(envelope).toByteArray(Charsets.UTF_8)

        val gz = ByteArrayOutputStream().also { bos ->
            GZIPOutputStream(bos).use { it.write(json) }
        }.toByteArray()
        File(outDir, "$name.json.gz").writeBytes(gz)

        val sha = MessageDigest.getInstance("SHA-256").digest(json)
            .joinToString("") { "%02x".format(it) }
        File(outDir, "$name.meta").writeText(
            buildString {
                append("lastModifiedDate:").append(NvdTime.iso()).append('\n')
                append("size:").append(json.size).append('\n')
                append("zipSize:").append(gz.size).append('\n')
                append("gzSize:").append(gz.size).append('\n')
                append("sha256:").append(sha).append('\n')
            }
        )
    }
}
