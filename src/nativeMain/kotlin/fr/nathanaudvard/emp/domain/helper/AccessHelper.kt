package fr.nathanaudvard.emp.domain.helper

import fr.nathanaudvard.emp.database.AccessRepository
import fr.nathanaudvard.emp.domain.model.AccessLink
import fr.nathanaudvard.emp.domain.model.DeviceCode
import fr.nathanaudvard.emp.domain.model.Session
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

// Renewed at each visit
val SESSION_DURATION = 365.days

val DEVICE_CODE_DURATION = 10.minutes

// Tokens are only stored hashed: the database alone opens no session
class AccessHelper(private val repository: AccessRepository) {
    // The token is only known from the returned value
    suspend fun issueLink(playerId: Long, createdBy: Long?, now: Instant): String {
        val token = newToken()
        repository.issueLink(playerId, hashToken(token), createdBy, now)
        return token
    }

    suspend fun link(token: String): AccessLink? = repository.link(hashToken(token))

    suspend fun openSession(playerId: Long, accessLinkId: Long, now: Instant): String {
        val token = newToken()
        repository.openSession(hashToken(token), playerId, accessLinkId, now, now + SESSION_DURATION)
        return token
    }

    suspend fun session(token: String, now: Instant): Session? =
        repository.session(hashToken(token))?.takeIf { it.expiresAt > now }

    suspend fun renewSession(session: Session, now: Instant) =
        repository.renewSession(session, now, now + SESSION_DURATION)

    suspend fun createDeviceCode(playerId: Long, accessLinkId: Long, now: Instant): DeviceCode {
        val code = DeviceCode(newDeviceCode(), now + DEVICE_CODE_DURATION)
        repository.saveDeviceCode(code.code, playerId, accessLinkId, now, code.expiresAt)
        return code
    }
}
