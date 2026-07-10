package pl.forestdev.nvd.service

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.file.shouldExist
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import pl.forestdev.nvd.db.CveRepository
import pl.forestdev.nvd.db.DatabaseFactory
import java.io.File
import java.nio.file.Files

class DatafeedGeneratorTest : StringSpec() {
    init {
        val db = Database.connect("jdbc:sqlite:datafeed_test.db", driver = "org.sqlite.JDBC")
        DatabaseFactory.initSchema(db)
        val repo = CveRepository(db)
        val tempDir = Files.createTempDirectory("nvd_test").toFile()
        val generator = DatafeedGenerator(db, tempDir)

        "should generate feed files" {
            // Seed some data
            val cve = JsonObject(mapOf(
                "id" to JsonPrimitive("CVE-2023-1234"),
                "lastModified" to JsonPrimitive("2023-01-01T00:00:00.000")
            ))
            repo.persistPage(listOf(JsonObject(mapOf("cve" to cve))))

            generator.generate(2023)

            File(tempDir, "nvdcve-2023.json.gz").shouldExist()
            File(tempDir, "nvdcve-2023.meta").shouldExist()
            File(tempDir, "cache.properties").shouldExist()
        }

        afterSpec {
            tempDir.deleteRecursively()
            File("datafeed_test.db").delete()
        }
    }
}
