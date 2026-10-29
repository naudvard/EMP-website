package fr.nathanaudvard.emp.domain.usecase

import fr.nathanaudvard.emp.domain.model.LevelChange
import fr.nathanaudvard.emp.domain.model.PlayerLevel
import fr.nathanaudvard.emp.service.PlayerService
import kotlin.time.Clock

// By is the player or a chief. Null when the level was already the one saved
class SavePlayerLevelUseCase(private val players: PlayerService, private val clock: Clock) {
    suspend operator fun invoke(playerId: Long, kind: PlayerLevel, level: Long, by: Long): LevelChange? =
        players.saveLevel(playerId, kind, level, by, clock.now())
}
