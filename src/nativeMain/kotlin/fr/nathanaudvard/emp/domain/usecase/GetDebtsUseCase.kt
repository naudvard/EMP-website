package fr.nathanaudvard.emp.domain.usecase

import fr.nathanaudvard.emp.domain.model.AllianceMember
import fr.nathanaudvard.emp.service.PlayerService

class GetDebtsUseCase(private val players: PlayerService) {
    suspend operator fun invoke(members: List<AllianceMember>): Map<Long, Long> = players.debts(members)
}
