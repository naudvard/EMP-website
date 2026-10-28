package fr.nathanaudvard.emp.domain.usecase

import fr.nathanaudvard.emp.service.AccessService
import fr.nathanaudvard.emp.service.PlayerService
import kotlin.time.Clock

// The first chief has no one to send them a link: it is made from the command line.
// Null when no member of the alliance has this pseudo
class CreateChiefLinkUseCase(
    private val access: AccessService,
    private val players: PlayerService,
    private val clock: Clock,
) {
    suspend operator fun invoke(allianceTag: String, pseudo: String): String? {
        val member = players.latestMembers(allianceTag).find { it.pseudo.equals(pseudo, ignoreCase = true) } ?: return null
        access.setChief(member.id, true)
        return access.issueLink(member.id, createdBy = null, clock.now())
    }
}
