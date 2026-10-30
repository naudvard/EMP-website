package fr.nathanaudvard.emp.domain.service

import fr.nathanaudvard.emp.database.AccessRepository
import fr.nathanaudvard.emp.database.ExportedPlayerRepository
import fr.nathanaudvard.emp.database.PlayerRepository
import fr.nathanaudvard.emp.domain.helper.AccessHelper
import fr.nathanaudvard.emp.domain.helper.normalizeDeviceCode
import fr.nathanaudvard.emp.domain.model.AllianceMember
import fr.nathanaudvard.emp.domain.model.DeviceCode
import fr.nathanaudvard.emp.domain.model.Identification
import fr.nathanaudvard.emp.domain.model.SignIn
import fr.nathanaudvard.emp.domain.model.Viewer
import kotlin.time.Clock

class AccessService(
    private val access: AccessHelper,
    private val accessRepository: AccessRepository,
    private val players: PlayerRepository,
    private val exportedPlayers: ExportedPlayerRepository,
    private val clock: Clock,
) {
    // Each visit renews the session: it only expires a year after the latest one
    suspend fun identify(allianceTag: String, sessionToken: String): Identification {
        val now = clock.now()
        val session = access.session(sessionToken, now) ?: return Identification.Unknown
        val member = exportedPlayers.latestMembers(allianceTag).find { it.id == session.playerId }
            ?: return Identification.Left
        access.renewSession(session, now)
        val chief = member.id in players.chiefs(listOf(member.id))
        return Identification.Member(Viewer(member.id, member.pseudo, chief, session.accessLinkId))
    }

    // A link's token opens a single session: the other devices are signed in with a code shown on that one, and a new
    // message holds a new token
    suspend fun openLink(allianceTag: String, token: String): SignIn {
        val link = access.link(token) ?: return SignIn.InvalidLink
        if (exportedPlayers.latestMembers(allianceTag).none { it.id == link.playerId }) return SignIn.Left
        val now = clock.now()
        if (link.usedAt != null || !accessRepository.useLinkToken(link.id, now)) return SignIn.UsedLink
        return SignIn.Opened(access.openSession(link.playerId, link.id, now))
    }

    // The new session comes from the same link as the one that showed the code: replacing the link closes both
    suspend fun signInWithCode(allianceTag: String, input: String): SignIn {
        val now = clock.now()
        val code = accessRepository.deviceCode(normalizeDeviceCode(input)) ?: return SignIn.UnknownCode
        if (code.usedAt != null || code.expiresAt <= now || !accessRepository.useDeviceCode(code.id, now)) {
            return SignIn.ExpiredCode
        }
        if (exportedPlayers.latestMembers(allianceTag).none { it.id == code.playerId }) return SignIn.Left
        return SignIn.Opened(access.openSession(code.playerId, code.accessLinkId, now))
    }

    suspend fun createDeviceCode(viewer: Viewer): DeviceCode =
        access.createDeviceCode(viewer.playerId, viewer.accessLinkId, clock.now())

    // The chiefs still in the alliance
    suspend fun chiefs(allianceTag: String): List<AllianceMember> {
        val members = exportedPlayers.latestMembers(allianceTag)
        val chiefs = players.chiefs(members.map { it.id })
        return members.filter { it.id in chiefs }
    }

    // The alliance always keeps a chief: false when the role would be taken from the last one, or the player is not a
    // member
    suspend fun setChief(allianceTag: String, playerId: Long, chief: Boolean): Boolean {
        val members = exportedPlayers.latestMembers(allianceTag).map { it.id }
        if (playerId !in members) return false
        if (!chief && players.chiefs(members) == setOf(playerId)) return false
        players.setChief(playerId, chief)
        return true
    }

    // Only the hash of a link's token is kept: each message gets a new token, created with the link when the player has
    // none, which opens a single session. The sessions opened before stay open, the previous message no longer opens any.
    // Null when the player is not in the alliance
    suspend fun sendLink(allianceTag: String, playerId: Long, chiefId: Long): Pair<AllianceMember, String>? {
        val member = exportedPlayers.latestMembers(allianceTag).find { it.id == playerId } ?: return null
        return member to access.issueLink(member.id, chiefId, clock.now())
    }

    // For a link given to someone else: its devices, and those signed in from them, are signed out, and the next message
    // copied holds a new link
    suspend fun replaceLink(playerId: Long) = accessRepository.revokeLinks(playerId, clock.now())

    // The first chief has no one to send them a link: it is made from the command line.
    // Null when no member of the alliance has this pseudo
    suspend fun createChiefLink(allianceTag: String, pseudo: String): String? {
        val member = exportedPlayers.latestMembers(allianceTag).find { it.pseudo.equals(pseudo, ignoreCase = true) }
            ?: return null
        players.setChief(member.id, true)
        return access.issueLink(member.id, createdBy = null, clock.now())
    }
}
