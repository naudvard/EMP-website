package fr.nathanaudvard.emp.database

import fr.nathanaudvard.emp.db.EmpDatabase
import fr.nathanaudvard.emp.domain.model.AccessLink
import fr.nathanaudvard.emp.domain.model.Session
import fr.nathanaudvard.emp.domain.model.StoredDeviceCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlin.time.Instant

class AccessRepository(database: EmpDatabase) {
    private val queries = database.accessQueries
    private val playerQueries = database.playerQueries

    // The player's link gets the new token: the sessions it opened stay open
    suspend fun issueLink(playerId: Long, tokenHash: String, createdBy: Long?, now: Instant) =
        withContext(Dispatchers.IO) {
            queries.transaction {
                val id = queries.activeLink(playerId).executeAsOneOrNull()
                if (id == null) {
                    queries.insertLink(playerId, tokenHash, now.toString(), createdBy, now.toString()).executeAsOne()
                } else {
                    queries.renewLinkToken(tokenHash, now.toString(), id)
                }
            }
        }

    suspend fun revokeLinks(playerId: Long, now: Instant) = withContext(Dispatchers.IO) {
        queries.transaction {
            queries.revokeLinks(now.toString(), playerId)
            queries.expireSessions(now.toString(), playerId)
        }
    }

    suspend fun link(tokenHash: String): AccessLink? = withContext(Dispatchers.IO) {
        queries.linkByTokenHash(tokenHash) { id, playerId, usedAt ->
            AccessLink(id, playerId, usedAt?.let(Instant::parse))
        }.executeAsOneOrNull()
    }

    // False when another request used it first
    suspend fun useLinkToken(id: Long, now: Instant): Boolean = withContext(Dispatchers.IO) {
        queries.useLinkToken(now.toString(), id).value > 0
    }

    suspend fun sentLinks(playerIds: Collection<Long>): Map<Long, Instant> = withContext(Dispatchers.IO) {
        queries.sentLinks(playerIds).executeAsList()
            .mapNotNull { row -> row.sent_at?.let { row.player_id to Instant.parse(it) } }
            .toMap()
    }

    suspend fun openSession(idHash: String, playerId: Long, accessLinkId: Long, now: Instant, expiresAt: Instant) =
        withContext(Dispatchers.IO) {
            queries.transaction {
                queries.insertSession(idHash, playerId, accessLinkId, now.toString(), now.toString(), expiresAt.toString())
                playerQueries.upsertLastSeen(playerId, now.toString())
            }
        }

    suspend fun session(idHash: String): Session? = withContext(Dispatchers.IO) {
        queries.session(idHash) { id, playerId, accessLinkId, expiresAt ->
            Session(id, playerId, accessLinkId, Instant.parse(expiresAt))
        }.executeAsOneOrNull()
    }

    suspend fun renewSession(session: Session, now: Instant, expiresAt: Instant) = withContext(Dispatchers.IO) {
        queries.transaction {
            queries.renewSession(now.toString(), expiresAt.toString(), session.id)
            playerQueries.upsertLastSeen(session.playerId, now.toString())
        }
    }

    suspend fun saveDeviceCode(code: String, playerId: Long, accessLinkId: Long, now: Instant, expiresAt: Instant) =
        withContext(Dispatchers.IO) {
            queries.insertDeviceCode(code, playerId, accessLinkId, now.toString(), expiresAt.toString())
        }

    suspend fun deviceCode(code: String): StoredDeviceCode? = withContext(Dispatchers.IO) {
        queries.deviceCode(code) { id, playerId, accessLinkId, expiresAt, usedAt ->
            StoredDeviceCode(id, playerId, accessLinkId, Instant.parse(expiresAt), usedAt?.let(Instant::parse))
        }.executeAsOneOrNull()
    }

    // False when another request used it first
    suspend fun useDeviceCode(id: Long, now: Instant): Boolean = withContext(Dispatchers.IO) {
        queries.useDeviceCode(now.toString(), id).value > 0
    }
}
