package pl.forestdev.nvd.api

import io.ktor.server.application.*
import io.ktor.server.http.content.staticFiles
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import pl.forestdev.nvd.client.NvdCveResponse
import pl.forestdev.nvd.db.Cves
import pl.forestdev.nvd.db.nvdJson
import pl.forestdev.nvd.util.NvdTime
import java.io.File

fun Application.configureRouting(db: Database, mirrorDir: File) {
    routing {
        get("/health") { call.respondText("ok") }

        get("/rest/json/cves/2.0") {
            val p = call.request.queryParameters
            val startIndex = p["startIndex"]?.toIntOrNull() ?: 0
            val perPage = (p["resultsPerPage"]?.toIntOrNull() ?: 2000).coerceIn(1, 2000)
            val modStart = p["lastModStartDate"]
            val modEnd = p["lastModEndDate"]
            val cveId = p["cveId"]

            val pred: Op<Boolean> = when {
                cveId != null -> Cves.id eq cveId
                modStart != null && modEnd != null ->
                    (Cves.lastModified greaterEq modStart) and (Cves.lastModified lessEq modEnd)
                else -> Op.TRUE
            }
            val orderCol = if (modStart != null) Cves.lastModified else Cves.id

            val (total, items) = withContext(Dispatchers.IO) {
                transaction(db) {
                    val totalCount = Cves.selectAll().where(pred).count().toInt()
                    val page = Cves.selectAll().where(pred)
                        .orderBy(orderCol to SortOrder.ASC, Cves.id to SortOrder.ASC)
                        .limit(perPage).offset(startIndex.toLong())
                        .map { JsonObject(mapOf("cve" to it[Cves.data])) }
                    totalCount to page
                }
            }

            call.respond(
                NvdCveResponse(
                    resultsPerPage = items.size,
                    startIndex = startIndex,
                    totalResults = total,
                    timestamp = NvdTime.now(),
                    vulnerabilities = items,
                )
            )
        }

        staticFiles("/nvd/mirror", mirrorDir)
    }
}

fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json(nvdJson)
    }
}
