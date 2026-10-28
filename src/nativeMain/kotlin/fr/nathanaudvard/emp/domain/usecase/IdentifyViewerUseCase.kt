package fr.nathanaudvard.emp.domain.usecase

import fr.nathanaudvard.emp.domain.model.Identification
import fr.nathanaudvard.emp.domain.model.Viewer
import fr.nathanaudvard.emp.service.AccessService
import fr.nathanaudvard.emp.service.PlayerService
import kotlin.time.Clock

// Each visit renews the session: it only expires a year after the latest one
class IdentifyViewerUseCase(
    private val access: AccessService,
    private val players: PlayerService,
    private val clock: Clock,
) {
    suspend operator fun invoke(allianceTag: String, sessionToken: String): Identification {
        val now = clock.now()
        val session = access.session(sessionToken, now) ?: return Identification.Unknown
        val member = players.latestMembers(allianceTag).find { it.id == session.playerId } ?: return Identification.Left
        access.renewSession(session, now)
        val chief = member.id in access.chiefs(listOf(member.id))
        return Identification.Member(Viewer(member.id, member.pseudo, chief, session.accessLinkId))
    }
}
