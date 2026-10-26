package fr.nathanaudvard.emp.domain.usecase

import fr.nathanaudvard.emp.domain.model.AllianceMember
import fr.nathanaudvard.emp.service.PlayerService

class GetAllianceMembersUseCase(private val players: PlayerService) {
    suspend operator fun invoke(allianceTag: String): List<AllianceMember> =
        players.latestMembers(allianceTag)
}
