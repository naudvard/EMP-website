package fr.nathanaudvard.emp.domain.usecase

import fr.nathanaudvard.emp.domain.exception.ExportNotFoundException
import fr.nathanaudvard.emp.domain.model.ExportType
import fr.nathanaudvard.emp.domain.model.ExportVersion
import fr.nathanaudvard.emp.domain.model.SyncResult
import fr.nathanaudvard.emp.service.ExportService

// The latest alliances and players exports, for the alliance only. The result is the players'
class SyncFourmizzzUseCase(private val exports: ExportService) {
    suspend operator fun invoke(allianceTag: String): SyncResult {
        val tags = setOf(allianceTag)
        exports.syncAlliances(latest(ExportType.ALLIANCES), tags)
        return exports.syncPlayers(latest(ExportType.PLAYERS), tags)
    }

    private suspend fun latest(type: ExportType): String =
        exports.resolve(type, ExportVersion.Latest) ?: throw ExportNotFoundException(type, "latest")
}
