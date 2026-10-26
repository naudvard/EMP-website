package fr.nathanaudvard.emp.domain.usecase

import fr.nathanaudvard.emp.domain.model.WageSettlement
import fr.nathanaudvard.emp.service.PlayerService

class SaveWageSettlementsUseCase(private val players: PlayerService) {
    suspend operator fun invoke(settlements: Map<Long, WageSettlement>) =
        players.saveWageSettlements(settlements)
}
