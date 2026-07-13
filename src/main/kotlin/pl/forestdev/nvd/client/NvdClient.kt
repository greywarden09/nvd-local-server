package pl.forestdev.nvd.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject


@Serializable
data class NvdCveResponse(
    val resultsPerPage: Int,
    val startIndex: Int,
    val totalResults: Int,
    val format: String = "NVD_CVE",
    val version: String = "2.0",
    val timestamp: String,
    val vulnerabilities: List<JsonObject>,
)

data class NvdConfig(
    val baseUrl: String,
    val apiKey: String?,
    val resultsPerPage: Int = 2000,
) {
    val minIntervalMs: Long = if (apiKey != null) 700L else 6_000L
}

fun nvdConfigFromEnv(baseUrl: String, resultsPerPage: Int = 2000): NvdConfig =
    NvdConfig(
        baseUrl = baseUrl,
        apiKey = System.getenv("NVD_API_KEY")?.takeIf { it.isNotBlank() },
        resultsPerPage = resultsPerPage,
    )

fun buildNvdClient(cfg: NvdConfig): HttpClient = HttpClient(CIO) {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
    install(HttpTimeout) {
        requestTimeoutMillis = 300_000
        connectTimeoutMillis = 15_000
        socketTimeoutMillis  = 60_000
    }
    install(HttpRequestRetry) {
        retryOnServerErrors(maxRetries = 5)
        retryIf { _, response -> response.status.value == 429 }
        retryOnExceptionIf(maxRetries = 5) { _, cause ->
            cause is HttpRequestTimeoutException || cause is java.io.IOException
        }
        exponentialDelay(base = 2.0, maxDelayMs = 30_000)
    }
    defaultRequest {
        url(cfg.baseUrl)
        accept(ContentType.Application.Json)
        cfg.apiKey?.let { header("apiKey", it) }
    }
}

suspend fun HttpClient.fetchCvePage(
    limiter: RateLimiter,
    startIndex: Int,
    resultsPerPage: Int,
    lastModStart: String? = null,
    lastModEnd: String? = null,
): NvdCveResponse = limiter.withPermit {
    val resp = get {
        parameter("startIndex", startIndex)
        parameter("resultsPerPage", resultsPerPage)
        lastModStart?.let { parameter("lastModStartDate", it) }
        lastModEnd?.let { parameter("lastModEndDate", it) }
    }
    if (resp.status == HttpStatusCode.Forbidden) {
        error("NVD 403: ${resp.headers["message"] ?: "no details"}")
    }
    resp.body()
}
