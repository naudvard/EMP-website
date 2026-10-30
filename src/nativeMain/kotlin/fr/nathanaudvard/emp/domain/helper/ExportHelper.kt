package fr.nathanaudvard.emp.domain.helper

import fr.nathanaudvard.emp.database.ExportedPlayerRepository
import fr.nathanaudvard.emp.database.PlayerRepository
import fr.nathanaudvard.emp.domain.model.ExportType
import fr.nathanaudvard.emp.domain.model.ExportVersion
import fr.nathanaudvard.emp.domain.model.SyncResult
import fr.nathanaudvard.emp.fourmizzz.FourmizzzClient
import kotlin.time.Instant

class ExportHelper(
    private val fourmizzz: FourmizzzClient,
    private val exportedPlayers: ExportedPlayerRepository,
    private val players: PlayerRepository,
) {
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

    // Newest first
    suspend fun versions(type: ExportType): List<String> {
        val allVersions = fourmizzz.versions()
        return when (type) {
            ExportType.PLAYERS -> allVersions.players
            ExportType.ALLIANCES -> allVersions.alliances
        }
    }
}
