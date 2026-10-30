package fr.nathanaudvard.emp.domain.service

import fr.nathanaudvard.emp.database.ExportedAllianceRepository
import fr.nathanaudvard.emp.domain.exception.ExportNotFoundException
import fr.nathanaudvard.emp.domain.helper.ExportHelper
import fr.nathanaudvard.emp.domain.model.ExportType
import fr.nathanaudvard.emp.domain.model.ExportVersion
import fr.nathanaudvard.emp.domain.model.SyncResult
import fr.nathanaudvard.emp.fourmizzz.FourmizzzClient

class ExportService(
    private val fourmizzz: FourmizzzClient,
    private val exportedAlliances: ExportedAllianceRepository,
    private val exports: ExportHelper,
) {
    // Only the alliances with these tags are stored
    suspend fun syncAlliances(version: ExportVersion, tags: Set<String>): SyncResult {
        val resolved = resolve(ExportType.ALLIANCES, version)
        val alliances = fourmizzz.alliances(resolved).filter { it.tag in tags }
        exportedAlliances.replaceVersion(resolved, tags, alliances)
        return SyncResult(resolved, alliances.size)
    }

    suspend fun syncPlayers(version: ExportVersion, tags: Set<String>): SyncResult =
        exports.syncPlayers(resolve(ExportType.PLAYERS, version), tags)

    // "latest" must be resolved: the stored version has to be the real (UTC) export version
    private suspend fun resolve(type: ExportType, version: ExportVersion): String = when (version) {
        ExportVersion.Latest -> exports.versions(type).firstOrNull() ?: throw ExportNotFoundException(type, "latest")
        is ExportVersion.Exact -> version.value
    }
}
