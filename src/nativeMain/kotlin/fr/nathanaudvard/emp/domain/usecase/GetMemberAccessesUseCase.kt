package fr.nathanaudvard.emp.domain.usecase

import fr.nathanaudvard.emp.domain.model.MemberAccess
import fr.nathanaudvard.emp.service.AccessService
import fr.nathanaudvard.emp.service.PlayerService

class GetMemberAccessesUseCase(private val access: AccessService, private val players: PlayerService) {
    suspend operator fun invoke(allianceTag: String): List<MemberAccess> {
        val members = players.latestMembers(allianceTag)
        val ids = members.map { it.id }
        val chiefs = access.chiefs(ids)
        val sent = access.sentLinks(ids)
        val lastSeen = access.lastSeen(ids)
        return members.map { MemberAccess(it, it.id in chiefs, sent[it.id], lastSeen[it.id]) }
    }
}
