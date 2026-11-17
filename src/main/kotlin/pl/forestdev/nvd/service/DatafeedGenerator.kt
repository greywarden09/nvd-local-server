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
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import pl.forestdev.nvd.db.Cves
import pl.forestdev.nvd.db.nvdJson
import pl.forestdev.nvd.util.NvdTime
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
            writeFeed("$prefix$year", pred)
        }

        val cutoff = NvdTime.format(Instant.now().minus(Duration.ofDays(8)))
        writeFeed("${prefix}modified", Cves.lastModified greaterEq cutoff)

        File(outDir, "cache.properties").writeText(
            "lastModifiedDate=${NvdTime.iso()}\nprefix=$prefix\n"
        )
    }

    suspend fun generateAsync(currentYear: Int = Year.now().value) =
        withContext(Dispatchers.IO) { generate(currentYear) }

    private fun writeFeed(name: String, pred: Op<Boolean>) {
        val gzFile = File(outDir, "$name.json.gz")
        val sha = MessageDigest.getInstance("SHA-256")
        var size = 0L

        transaction(db) {
            val total = Cves.selectAll().where(pred).count().toInt()
            val header = nvdJson.encodeToString(
                NvdCveResponse(
                    resultsPerPage = total,
                    startIndex = 0,
                    totalResults = total,
                    timestamp = NvdTime.now(),
                    vulnerabilities = emptyList(),
                )
            ).removeSuffix("]}")

            GZIPOutputStream(gzFile.outputStream().buffered()).use { gz ->
                fun emit(text: String) {
                    val bytes = text.toByteArray(Charsets.UTF_8)
                    sha.update(bytes)
                    size += bytes.size
                    gz.write(bytes)
                }

                emit(header)
                var first = true
                Cves.select(Cves.data).where(pred)
                    .orderBy(Cves.id to SortOrder.ASC)
                    .forEach {
                        val item = JsonObject(mapOf("cve" to it[Cves.data]))
                        emit(if (first) "" else ",")
                        emit(nvdJson.encodeToString(JsonObject.serializer(), item))
                        first = false
                    }
                emit("]}")
            }
        }

        val gzSize = gzFile.length()
        File(outDir, "$name.meta").writeText(
            buildString {
                append("lastModifiedDate:").append(NvdTime.iso()).append('\n')
                append("size:").append(size).append('\n')
                append("zipSize:").append(gzSize).append('\n')
                append("gzSize:").append(gzSize).append('\n')
                append("sha256:").append(sha.digest().joinToString("") { "%02x".format(it) }).append('\n')
            }
        )
    }
}
