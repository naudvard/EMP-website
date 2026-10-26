package fr.nathanaudvard.emp.domain.usecase

import fr.nathanaudvard.emp.domain.exception.ExportNotFoundException
import fr.nathanaudvard.emp.domain.model.ExportType
import fr.nathanaudvard.emp.domain.model.ExportVersion
import fr.nathanaudvard.emp.domain.model.SyncResult
import fr.nathanaudvard.emp.service.ExportService

class SyncExportedPlayersUseCase(private val exports: ExportService) {
    suspend operator fun invoke(version: ExportVersion, tags: Set<String>): SyncResult {
        val resolved = exports.resolve(ExportType.PLAYERS, version)
            ?: throw ExportNotFoundException(ExportType.PLAYERS, "latest")
        return exports.syncPlayers(resolved, tags)
    }
}
