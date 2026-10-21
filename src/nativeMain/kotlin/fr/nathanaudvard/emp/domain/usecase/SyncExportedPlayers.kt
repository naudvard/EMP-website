package fr.nathanaudvard.emp.domain.usecase

import fr.nathanaudvard.emp.database.ExportedPlayerRepository
import fr.nathanaudvard.emp.domain.model.ExportType
import fr.nathanaudvard.emp.domain.model.ExportVersion
import fr.nathanaudvard.emp.domain.model.SyncResult
import fr.nathanaudvard.emp.fourmizzz.FourmizzzClient

class SyncExportedPlayers(
    private val fourmizzz: FourmizzzClient,
    private val repository: ExportedPlayerRepository,
) {
    suspend operator fun invoke(version: ExportVersion): SyncResult {
        val resolved = fourmizzz.resolve(ExportType.PLAYERS, version)
        val players = fourmizzz.players(resolved)
        repository.replaceVersion(resolved, players)
        return SyncResult(resolved, players.size)
    }
}
