package fr.nathanaudvard.emp.domain.usecase

import fr.nathanaudvard.emp.domain.model.WageSettlement
import fr.nathanaudvard.emp.service.PlayerService
import kotlin.time.Clock

// What the member does with a negative balance, from the next computation on. By is the chief who chose it.
// The former choice is returned, for it to be put back; null when the player is not a member
class SaveWageSettlementUseCase(private val players: PlayerService, private val clock: Clock) {
    suspend operator fun invoke(allianceTag: String, playerId: Long, settlement: WageSettlement, by: Long): WageSettlement? {
        val member = players.latestMembers(allianceTag).find { it.id == playerId } ?: return null
        val former = players.states(listOf(member)).getValue(playerId).settlement
        players.saveWageSettlement(playerId, settlement, by, clock.now())
        return former
    }
}
