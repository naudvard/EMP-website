package fr.nathanaudvard.emp.domain.usecase

import fr.nathanaudvard.emp.domain.model.Alliance
import fr.nathanaudvard.emp.domain.model.ExportType
import fr.nathanaudvard.emp.domain.model.ExportVersion
import fr.nathanaudvard.emp.domain.port.AllianceRepository
import fr.nathanaudvard.emp.domain.port.ExportSource

class SyncAlliances(
    private val exports: ExportSource,
    private val repository: AllianceRepository,
) {
    suspend operator fun invoke(version: ExportVersion): List<Alliance> {
        val resolved = exports.resolve(ExportType.ALLIANCES, version)
        return repository.saveVersion(resolved, exports.alliances(resolved))
    }
}
