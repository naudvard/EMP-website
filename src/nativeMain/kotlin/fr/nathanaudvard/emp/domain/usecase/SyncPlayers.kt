package fr.nathanaudvard.emp.domain.usecase

import fr.nathanaudvard.emp.domain.model.ExportType
import fr.nathanaudvard.emp.domain.model.ExportVersion
import fr.nathanaudvard.emp.domain.model.Player
import fr.nathanaudvard.emp.domain.port.ExportSource
import fr.nathanaudvard.emp.domain.port.PlayerRepository

class SyncPlayers(
    private val exports: ExportSource,
    private val repository: PlayerRepository,
) {
    suspend operator fun invoke(version: ExportVersion): List<Player> {
        val resolved = exports.resolve(ExportType.PLAYERS, version)
        return exports.players(resolved).also { repository.replaceVersion(resolved, it) }
    }
}
