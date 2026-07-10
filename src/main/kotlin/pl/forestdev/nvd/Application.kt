package pl.forestdev.nvd

import io.ktor.server.application.Application
import io.ktor.server.application.log
import io.ktor.server.config.yaml.YamlConfig
import io.ktor.server.netty.EngineMain
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.slf4j.LoggerFactory
import pl.forestdev.nvd.api.configureRouting
import pl.forestdev.nvd.api.configureSerialization
import pl.forestdev.nvd.client.RateLimiter
import pl.forestdev.nvd.client.buildNvdClient
import pl.forestdev.nvd.client.nvdConfigFromEnv
import pl.forestdev.nvd.db.CveRepository
import pl.forestdev.nvd.db.DatabaseFactory
import pl.forestdev.nvd.service.DatafeedGenerator
import pl.forestdev.nvd.service.SyncScheduler
import pl.forestdev.nvd.service.SyncSchedulerService
import pl.forestdev.nvd.service.SyncService
import java.io.File
import java.sql.Connection

fun Application.module() {
    val dbUrl = environment.config.property("nvd.db.url").getString()
    val db = DatabaseFactory.connect(dbUrl)
    TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_SERIALIZABLE
    DatabaseFactory.initSchema(db)

    val nvdConfig = nvdConfigFromEnv(environment.config.property("nvd.api.baseUrl").getString())
    val client = buildNvdClient(nvdConfig)
    val limiter = RateLimiter(nvdConfig.minIntervalMs)
    log.info("NVD: mode {}", if (nvdConfig.apiKey != null) "with API key" else "public")

    val mirrorDir = File("data/mirror")
    val repository = CveRepository(db)
    val schedulerService = SyncSchedulerService(db)
    val datafeed = DatafeedGenerator(db, mirrorDir)
    val syncService = SyncService(client, limiter, nvdConfig, db, repository, schedulerService, log)
    val scheduler = SyncScheduler(syncService, log)

    with(scheduler) {
        schedule {
            datafeed.generateAsync()
            log.info("datafeed rebuilt: {}", mirrorDir.absolutePath)
        }
    }

    configureSerialization()
    configureRouting(db, mirrorDir)
}

fun main(args: Array<String>) {
    if (args.contains("--sync-and-exit")) {
        runSyncAndExit()
        return
    }
    EngineMain.main(args)
}

private fun runSyncAndExit() = runBlocking {
    val log = LoggerFactory.getLogger("NvdSync")

    val config = YamlConfig("application.yaml")
        ?: error("application.yaml not found on classpath")

    val dbUrl = config.property("nvd.db.url").getString()
    val baseUrl = config.property("nvd.api.baseUrl").getString()

    File("data").mkdirs()
    val db = DatabaseFactory.connect(dbUrl)
    DatabaseFactory.initSchema(db)

    val nvdConfig = nvdConfigFromEnv(baseUrl)
    val client = buildNvdClient(nvdConfig)
    val limiter = RateLimiter(nvdConfig.apiKey?.let { 700L } ?: 6000L) // Or nvdConfig.minIntervalMs

    log.info("NVD sync (one-shot mode): {}", if (nvdConfig.apiKey != null) "with API key" else "public")

    val repository = CveRepository(db)
    val schedulerService = SyncSchedulerService(db)
    val syncService = SyncService(client, limiter, nvdConfig, db, repository, schedulerService, log)

    client.use {
        syncService.runSync()
        DatafeedGenerator(db, File("data/mirror")).generate()
        log.info("Sync completed, datafeed rebuilt")
    }
}
