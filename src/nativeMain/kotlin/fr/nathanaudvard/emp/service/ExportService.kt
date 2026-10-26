package fr.nathanaudvard.emp.service

import fr.nathanaudvard.emp.database.ExportedAllianceRepository
import fr.nathanaudvard.emp.database.ExportedPlayerRepository
import fr.nathanaudvard.emp.database.PlayerRepository
import fr.nathanaudvard.emp.domain.model.ExportType
import fr.nathanaudvard.emp.domain.model.ExportVersion
import fr.nathanaudvard.emp.domain.model.SyncResult
import fr.nathanaudvard.emp.fourmizzz.FourmizzzClient
import kotlin.time.Instant

class ExportService(
    private val fourmizzz: FourmizzzClient,
    private val exportedAlliances: ExportedAllianceRepository,
    private val exportedPlayers: ExportedPlayerRepository,
    private val players: PlayerRepository,
) {
    // "latest" must be resolved: the stored version has to be the real (UTC) export version. Null when there is no export
    suspend fun resolve(type: ExportType, version: ExportVersion): String? = when (version) {
        ExportVersion.Latest -> latestVersion(type)
        is ExportVersion.Exact -> version.value
    }

    // Only the alliances with these tags are stored
    suspend fun syncAlliances(version: String, tags: Set<String>): SyncResult {
        val alliances = fourmizzz.alliances(version).filter { it.tag in tags }
        exportedAlliances.replaceVersion(version, tags, alliances)
        return SyncResult(version, alliances.size)
    }

    // Only the members of these alliances are stored. They are created as members right away, so that their
    // settlement can be chosen before any wage computation
    suspend fun syncPlayers(version: String, tags: Set<String>): SyncResult {
        val members = fourmizzz.players(version).filter { it.allianceTag in tags }
        exportedPlayers.replaceVersion(version, tags, members)
        players.createMembers(members.map { it.id })
        return SyncResult(version, members.size)
    }

    // Stores the members of the alliance from the players exports Fourmizzz has since this instant and that are not
    // stored for it yet, then returns them all
    suspend fun syncPlayersSince(instant: Instant, allianceTag: String): List<String> {
        val since = ExportVersion.of(instant)
        val available = versions(ExportType.PLAYERS).filter { it >= since }
        val stored = exportedPlayers.versionsSince(allianceTag, since)
        (available - stored.toSet()).forEach { syncPlayers(it, setOf(allianceTag)) }
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
