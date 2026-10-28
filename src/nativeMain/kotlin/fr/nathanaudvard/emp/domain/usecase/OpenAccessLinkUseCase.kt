package fr.nathanaudvard.emp.domain.usecase

import fr.nathanaudvard.emp.domain.model.SignIn
import fr.nathanaudvard.emp.service.AccessService
import fr.nathanaudvard.emp.service.PlayerService
import kotlin.time.Clock

// A link opens a session on each device it is opened on, until it is replaced
class OpenAccessLinkUseCase(
    private val access: AccessService,
    private val players: PlayerService,
    private val clock: Clock,
) {
    suspend operator fun invoke(allianceTag: String, token: String): SignIn {
        val link = access.link(token) ?: return SignIn.InvalidLink
        if (players.latestMembers(allianceTag).none { it.id == link.playerId }) return SignIn.Left
        return SignIn.Opened(access.openSession(link.playerId, link.id, clock.now()))
    }
}
