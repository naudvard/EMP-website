package fr.nathanaudvard.emp.domain.usecase

import fr.nathanaudvard.emp.database.AllianceRepository
import fr.nathanaudvard.emp.domain.model.ExportType
import fr.nathanaudvard.emp.domain.model.ExportVersion
import fr.nathanaudvard.emp.domain.model.SyncResult
import fr.nathanaudvard.emp.fourmizzz.FourmizzzClient

class SyncAlliances(
    private val fourmizzz: FourmizzzClient,
    private val repository: AllianceRepository,
) {
    suspend operator fun invoke(version: ExportVersion): SyncResult {
        val resolved = fourmizzz.resolve(ExportType.ALLIANCES, version)
        val alliances = fourmizzz.alliances(resolved)
        repository.replaceVersion(resolved, alliances)
        return SyncResult(resolved, alliances.size)
    }
}
