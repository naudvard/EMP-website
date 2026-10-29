package fr.nathanaudvard.emp.domain.usecase

import fr.nathanaudvard.emp.service.PlayerService
import kotlin.time.Clock

// The levels did not change: only their date is renewed
class ConfirmPlayerLevelsUseCase(private val players: PlayerService, private val clock: Clock) {
    suspend operator fun invoke(playerId: Long, by: Long) = players.confirmLevels(playerId, by, clock.now())
}
