package fr.nathanaudvard.emp.domain.usecase

import fr.nathanaudvard.emp.service.AccessService
import fr.nathanaudvard.emp.service.PlayerService

// The alliance always keeps a chief: false when the role would be taken from the last one, or the player is not a
// member
class SetChiefUseCase(private val access: AccessService, private val players: PlayerService) {
    suspend operator fun invoke(allianceTag: String, playerId: Long, chief: Boolean): Boolean {
        val members = players.latestMembers(allianceTag).map { it.id }
        if (playerId !in members) return false
        if (!chief && access.chiefs(members) == setOf(playerId)) return false
        access.setChief(playerId, chief)
        return true
    }
}
