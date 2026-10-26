package fr.nathanaudvard.emp.domain.usecase

import fr.nathanaudvard.emp.domain.model.PlayerLevel
import fr.nathanaudvard.emp.service.PlayerService

class SavePlayerLevelUseCase(private val players: PlayerService) {
    suspend operator fun invoke(playerId: Long, kind: PlayerLevel, level: Long) =
        players.saveLevel(playerId, kind, level)
}
