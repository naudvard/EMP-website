package fr.nathanaudvard.emp.domain.usecase

import fr.nathanaudvard.emp.domain.model.SignIn
import fr.nathanaudvard.emp.service.AccessService
import fr.nathanaudvard.emp.service.PlayerService
import fr.nathanaudvard.emp.service.normalizeDeviceCode
import kotlin.time.Clock

// The new session comes from the same link as the one that showed the code: replacing the link closes both
class SignInWithCodeUseCase(
    private val access: AccessService,
    private val players: PlayerService,
    private val clock: Clock,
) {
    suspend operator fun invoke(allianceTag: String, input: String): SignIn {
        val now = clock.now()
        val code = access.deviceCode(normalizeDeviceCode(input)) ?: return SignIn.UnknownCode
        if (code.usedAt != null || code.expiresAt <= now || !access.useDeviceCode(code, now)) return SignIn.ExpiredCode
        if (players.latestMembers(allianceTag).none { it.id == code.playerId }) return SignIn.Left
        return SignIn.Opened(access.openSession(code.playerId, code.accessLinkId, now))
    }
}
