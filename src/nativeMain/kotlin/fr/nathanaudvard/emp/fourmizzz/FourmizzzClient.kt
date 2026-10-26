package fr.nathanaudvard.emp.fourmizzz

import fr.nathanaudvard.emp.config.FourmizzzConfig
import fr.nathanaudvard.emp.domain.exception.ExportNotFoundException
import fr.nathanaudvard.emp.domain.exception.ExportUnavailableException
import fr.nathanaudvard.emp.domain.exception.InvalidVersionException
import fr.nathanaudvard.emp.domain.model.ExportType
import fr.nathanaudvard.emp.domain.model.ExportedAlliance
import fr.nathanaudvard.emp.domain.model.ExportedPlayer
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

class FourmizzzClient(http: HttpClient, config: FourmizzzConfig) {
    // Configured here rather than at creation so that it survives a client substituted in tests
    private val http = http.config {
        expectSuccess = true
        defaultRequest { url(config.baseUrl) }
        install(HttpTimeout) {
            requestTimeoutMillis = config.timeoutMs
        }
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        HttpResponseValidator {
            handleResponseExceptionWithRequest { cause, request ->
                // Returning rethrows the original exception: cancellation must go through untouched
                if (cause is CancellationException) return@handleResponseExceptionWithRequest
                throw request.url.toExportException(cause)
            }
        }
    }

    // Newest first
    suspend fun versions(): ExportVersionsRes = http.get("api/exports/").body()

    suspend fun alliances(version: String): List<ExportedAlliance> =
        fetchExport<AllianceRes>(ExportType.ALLIANCES, version).map { it.toExportedAlliance() }

    suspend fun players(version: String): List<ExportedPlayer> =
        fetchExport<PlayerRes>(ExportType.PLAYERS, version).map { it.toExportedPlayer() }

    // Keep the trailing slash in paths: without it, Fourmizzz answers a 301 first
    private suspend inline fun <reified T> fetchExport(type: ExportType, version: String): List<T> =
        http.get("api/exports/${type.name.lowercase()}/") { parameter("version", version) }.body()
}

private fun Url.toExportException(cause: Throwable): Exception {
    val status = (cause as? ClientRequestException)?.response?.status
    val version = parameters["version"] ?: "latest"
    val type = ExportType.entries.find { it.name.lowercase() == segments.lastOrNull() }
    return when {
        status == null -> ExportUnavailableException("Fourmizzz $encodedPath is unavailable", cause)
        type != null && status == HttpStatusCode.BadRequest ->
            InvalidVersionException("Fourmizzz rejected export version $version")

        type != null && status == HttpStatusCode.NotFound -> ExportNotFoundException(type, version)
        else -> ExportUnavailableException("Fourmizzz $encodedPath failed: $status", cause)
    }
}
