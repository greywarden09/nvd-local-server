package pl.forestdev.nvd.service

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.jetbrains.exposed.v1.jdbc.Database
import pl.forestdev.nvd.db.DatabaseFactory

class SyncSchedulerServiceTest : StringSpec() {
    init {
        val db = Database.connect("jdbc:sqlite:test.db", driver = "org.sqlite.JDBC")
        DatabaseFactory.initSchema(db)
        val service = SyncSchedulerService(db)

        "should save and load resume state" {
            service.saveResumeWindow("target", "2023-01-01", "2023-01-02", 5)
            
            val state = service.loadResumeState()
            state shouldNotBe null
            state?.target shouldBe "target"
            state?.from shouldBe "2023-01-01"
            state?.to shouldBe "2023-01-02"
            state?.index shouldBe 5
        }

        "should update resume index" {
            service.saveResumeWindow("target", "2023-01-01", "2023-01-02", 5)
            service.saveResumeIndex(10)
            
            val state = service.loadResumeState()
            state?.index shouldBe 10
        }

        "should clear resume state" {
            service.saveResumeWindow("target", "2023-01-01", "2023-01-02", 5)
            service.clearResumeState()
            
            service.loadResumeState() shouldBe null
        }

        afterSpec {
            java.io.File("test.db").delete()
        }
    }
}
