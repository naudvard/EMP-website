package fr.nathanaudvard.emp.domain.model

import kotlin.time.Instant

// The member a browser's session belongs to
data class Viewer(val playerId: Long, val pseudo: String, val chief: Boolean, val accessLinkId: Long) {
    // A member acts for themself, a chief for everyone
    fun canEdit(playerId: Long) = chief || playerId == this.playerId
}

sealed interface Identification {
    data class Member(val viewer: Viewer) : Identification

    // A valid session of a player who is no longer in the alliance
    data object Left : Identification

    data object Unknown : Identification
}

sealed interface SignIn {
    data class Opened(val sessionToken: String) : SignIn

    data object InvalidLink : SignIn

    data object Left : SignIn

    data object UnknownCode : SignIn

    data object ExpiredCode : SignIn

    data object TooManyAttempts : SignIn
}

data class AccessLink(val id: Long, val playerId: Long)

data class Session(val id: Long, val playerId: Long, val accessLinkId: Long, val expiresAt: Instant)

data class DeviceCode(val code: String, val expiresAt: Instant)

data class StoredDeviceCode(
    val id: Long,
    val playerId: Long,
    val accessLinkId: Long,
    val expiresAt: Instant,
    val usedAt: Instant?,
)

// The latest visit and when the link's message was last copied, null when never
data class MemberAccess(val member: AllianceMember, val chief: Boolean, val sentAt: Instant?, val lastSeenAt: Instant?)
