package fr.nathanaudvard.emp.domain.usecase

import fr.nathanaudvard.emp.domain.model.AllianceMember
import fr.nathanaudvard.emp.service.AccessService
import fr.nathanaudvard.emp.service.PlayerService

// The chiefs still in the alliance
class GetChiefsUseCase(private val access: AccessService, private val players: PlayerService) {
    suspend operator fun invoke(allianceTag: String): List<AllianceMember> {
        val members = players.latestMembers(allianceTag)
        val chiefs = access.chiefs(members.map { it.id })
        return members.filter { it.id in chiefs }
    }
}
