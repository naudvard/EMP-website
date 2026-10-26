package fr.nathanaudvard.emp.domain.usecase

import fr.nathanaudvard.emp.domain.exception.ExportNotFoundException
import fr.nathanaudvard.emp.domain.model.ExportType
import fr.nathanaudvard.emp.domain.model.ExportVersion
import fr.nathanaudvard.emp.domain.model.SyncResult
import fr.nathanaudvard.emp.service.ExportService

class SyncExportedAlliancesUseCase(private val exports: ExportService) {
    suspend operator fun invoke(version: ExportVersion): SyncResult {
        val resolved = exports.resolve(ExportType.ALLIANCES, version)
            ?: throw ExportNotFoundException(ExportType.ALLIANCES, "latest")
        return exports.syncAlliances(resolved)
    }
}
