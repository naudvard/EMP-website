package fr.nathanaudvard.emp.domain.usecase

import fr.nathanaudvard.emp.service.PlayerService
import kotlin.time.Clock

// Puts the former level back, as long as the level is still the one the change saved
class UndoLevelChangeUseCase(private val players: PlayerService, private val clock: Clock) {
    suspend operator fun invoke(changeId: Long, playerId: Long, by: Long) {
        val change = players.levelChange(changeId)?.takeIf { it.playerId == playerId } ?: return
        if (players.levelsState(playerId).levels[change.kind] != change.to) return
        players.saveLevel(playerId, change.kind, change.from, by, clock.now())
    }
}
