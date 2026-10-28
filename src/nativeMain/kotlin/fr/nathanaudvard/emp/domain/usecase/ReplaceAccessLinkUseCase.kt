package fr.nathanaudvard.emp.domain.usecase

import fr.nathanaudvard.emp.service.AccessService
import kotlin.time.Clock

// For a link given to someone else: its devices are signed out, and the next message copied holds a new link
class ReplaceAccessLinkUseCase(private val access: AccessService, private val clock: Clock) {
    suspend operator fun invoke(playerId: Long) = access.revokeLinks(playerId, clock.now())
}
