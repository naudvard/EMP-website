package fr.nathanaudvard.emp.domain.usecase

import fr.nathanaudvard.emp.domain.model.AllianceMember
import fr.nathanaudvard.emp.service.AccessService
import fr.nathanaudvard.emp.service.PlayerService
import kotlin.time.Clock

// Only the hash of a link's token is kept: each message gets a new token, created with the link when the player has
// none. The sessions opened before stay open, the previous message no longer opens any.
// Null when the player is not in the alliance
class SendAccessLinkUseCase(
    private val access: AccessService,
    private val players: PlayerService,
    private val clock: Clock,
) {
    suspend operator fun invoke(allianceTag: String, playerId: Long, chiefId: Long): Pair<AllianceMember, String>? {
        val member = players.latestMembers(allianceTag).find { it.id == playerId } ?: return null
        return member to access.issueLink(member.id, chiefId, clock.now())
    }
}
