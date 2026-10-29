package fr.nathanaudvard.emp.domain.usecase

import fr.nathanaudvard.emp.domain.model.SignIn
import fr.nathanaudvard.emp.service.AccessService
import fr.nathanaudvard.emp.service.PlayerService
import kotlin.time.Clock

// A link's token opens a single session: the other devices are signed in with a code shown on that one, and a new
// message holds a new token
class OpenAccessLinkUseCase(
    private val access: AccessService,
    private val players: PlayerService,
    private val clock: Clock,
) {
    suspend operator fun invoke(allianceTag: String, token: String): SignIn {
        val link = access.link(token) ?: return SignIn.InvalidLink
        if (players.latestMembers(allianceTag).none { it.id == link.playerId }) return SignIn.Left
        val now = clock.now()
        if (link.usedAt != null || !access.useLinkToken(link, now)) return SignIn.UsedLink
        return SignIn.Opened(access.openSession(link.playerId, link.id, now))
    }
}
