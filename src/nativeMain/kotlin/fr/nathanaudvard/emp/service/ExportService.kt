package fr.nathanaudvard.emp.service

import fr.nathanaudvard.emp.database.ExportedAllianceRepository
import fr.nathanaudvard.emp.database.ExportedPlayerRepository
import fr.nathanaudvard.emp.domain.model.ExportType
import fr.nathanaudvard.emp.domain.model.ExportVersion
import fr.nathanaudvard.emp.domain.model.SyncResult
import fr.nathanaudvard.emp.fourmizzz.FourmizzzClient
import kotlin.time.Instant

class ExportService(
    private val fourmizzz: FourmizzzClient,
    private val exportedAlliances: ExportedAllianceRepository,
    private val exportedPlayers: ExportedPlayerRepository,
) {
    // "latest" must be resolved: the stored version has to be the real (UTC) export version. Null when there is no export
    suspend fun resolve(type: ExportType, version: ExportVersion): String? = when (version) {
        ExportVersion.Latest -> latestVersion(type)
        is ExportVersion.Exact -> version.value
    }

    suspend fun syncAlliances(version: String): SyncResult {
        val alliances = fourmizzz.alliances(version)
        exportedAlliances.replaceVersion(version, alliances)
        return SyncResult(version, alliances.size)
    }

    suspend fun syncPlayers(version: String): SyncResult {
        val players = fourmizzz.players(version)
        exportedPlayers.replaceVersion(version, players)
        return SyncResult(version, players.size)
    }

    // Stores the players exports Fourmizzz has since this instant and that are not stored yet, then returns them all
    suspend fun syncPlayersSince(instant: Instant): List<String> {
        val since = ExportVersion.of(instant)
        val available = versions(ExportType.PLAYERS).filter { it >= since }
        val stored = exportedPlayers.versionsSince(since)
        (available - stored.toSet()).forEach { syncPlayers(it) }
        return (available + stored).distinct()
    }

    fun latestOfEachHour(versions: List<String>): List<String> = versions.groupBy { it.take(10) }
        .values
        .map { it.max() }

    private suspend fun latestVersion(type: ExportType): String? = versions(type).firstOrNull()

    // Newest first
    private suspend fun versions(type: ExportType): List<String> {
        val allVersions = fourmizzz.versions()
        return when (type) {
            ExportType.PLAYERS -> allVersions.players
            ExportType.ALLIANCES -> allVersions.alliances
        }
    }
}
