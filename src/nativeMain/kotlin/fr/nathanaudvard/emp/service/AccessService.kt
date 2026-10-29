package fr.nathanaudvard.emp.service

import fr.nathanaudvard.emp.database.AccessRepository
import fr.nathanaudvard.emp.database.PlayerRepository
import fr.nathanaudvard.emp.domain.model.AccessLink
import fr.nathanaudvard.emp.domain.model.DeviceCode
import fr.nathanaudvard.emp.domain.model.Session
import fr.nathanaudvard.emp.domain.model.StoredDeviceCode
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

// Renewed at each visit
val SESSION_DURATION = 365.days

val DEVICE_CODE_DURATION = 10.minutes

// Tokens are only stored hashed: the database alone opens no session
class AccessService(
    private val access: AccessRepository,
    private val players: PlayerRepository,
) {
    // The token is only known from the returned value
    suspend fun issueLink(playerId: Long, createdBy: Long?, now: Instant): String {
        val token = newToken()
        access.issueLink(playerId, hashToken(token), createdBy, now)
        return token
    }

    // Closes the sessions of the player's link, and those opened from them
    suspend fun revokeLinks(playerId: Long, now: Instant) = access.revokeLinks(playerId, now)

    suspend fun link(token: String): AccessLink? = access.link(hashToken(token))

    suspend fun useLinkToken(link: AccessLink, now: Instant): Boolean = access.useLinkToken(link.id, now)

    suspend fun openSession(playerId: Long, accessLinkId: Long, now: Instant): String {
        val token = newToken()
        access.openSession(hashToken(token), playerId, accessLinkId, now, now + SESSION_DURATION)
        return token
    }

    suspend fun session(token: String, now: Instant): Session? =
        access.session(hashToken(token))?.takeIf { it.expiresAt > now }

    suspend fun renewSession(session: Session, now: Instant) =
        access.renewSession(session, now, now + SESSION_DURATION)

    suspend fun createDeviceCode(playerId: Long, accessLinkId: Long, now: Instant): DeviceCode {
        val code = DeviceCode(newDeviceCode(), now + DEVICE_CODE_DURATION)
        access.saveDeviceCode(code.code, playerId, accessLinkId, now, code.expiresAt)
        return code
    }

    suspend fun deviceCode(code: String): StoredDeviceCode? = access.deviceCode(code)

    suspend fun useDeviceCode(code: StoredDeviceCode, now: Instant): Boolean = access.useDeviceCode(code.id, now)

    suspend fun sentLinks(playerIds: Collection<Long>): Map<Long, Instant> = access.sentLinks(playerIds)

    suspend fun chiefs(playerIds: Collection<Long>): Set<Long> = players.chiefs(playerIds)

    suspend fun setChief(playerId: Long, chief: Boolean) = players.setChief(playerId, chief)
}
