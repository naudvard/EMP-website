package fr.nathanaudvard.emp.domain.usecase

import fr.nathanaudvard.emp.domain.model.AllianceMember
import fr.nathanaudvard.emp.domain.model.WageSettlement
import fr.nathanaudvard.emp.service.PlayerService

class GetWageSettlementsUseCase(private val players: PlayerService) {
    suspend operator fun invoke(members: List<AllianceMember>): Map<Long, WageSettlement> =
        players.wageSettlements(members)
}
