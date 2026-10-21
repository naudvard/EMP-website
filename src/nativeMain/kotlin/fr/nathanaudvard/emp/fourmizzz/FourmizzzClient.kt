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
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
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
    }

    suspend fun latestVersion(type: ExportType): String =
        versions(type).firstOrNull() ?: throw ExportNotFoundException(type, "latest")

    // Newest first
    suspend fun versions(type: ExportType): List<String> = fetch<ExportVersionsRes>("api/exports/")[type]

    suspend fun alliances(version: String): List<ExportedAlliance> =
        fetchExport<AllianceRes>(ExportType.ALLIANCES, version).map { it.toExportedAlliance() }

    suspend fun players(version: String): List<ExportedPlayer> =
        fetchExport<PlayerRes>(ExportType.PLAYERS, version).map { it.toExportedPlayer() }

    private suspend inline fun <reified T> fetchExport(type: ExportType, version: String): List<T> =
        fetch("api/exports/${type.name.lowercase()}/", version) { status ->
            when (status) {
                HttpStatusCode.BadRequest -> InvalidVersionException("Fourmizzz rejected export version $version")
                HttpStatusCode.NotFound -> ExportNotFoundException(type, version)
                else -> null
            }
        }

    // Keep the trailing slash in paths: without it, Fourmizzz answers a 301 first
    private suspend inline fun <reified T> fetch(
        path: String,
        version: String? = null,
        onClientError: (HttpStatusCode) -> Exception? = { null },
    ): T =
        try {
            http.get(path) { version?.let { parameter("version", it) } }.body()
        } catch (e: ClientRequestException) {
            throw onClientError(e.response.status) ?: ExportUnavailableException("Fourmizzz /$path failed: ${e.response.status}", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw ExportUnavailableException("Fourmizzz /$path is unavailable", e)
        }
}
