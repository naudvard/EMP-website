package fr.nathanaudvard.emp.service

import fr.nathanaudvard.emp.database.ExportedPlayerRepository
import fr.nathanaudvard.emp.database.PlayerRepository
import fr.nathanaudvard.emp.domain.model.AllianceMember
import fr.nathanaudvard.emp.domain.model.LevelChange
import fr.nathanaudvard.emp.domain.model.PlayerLevel
import fr.nathanaudvard.emp.domain.model.PlayerLevels
import fr.nathanaudvard.emp.domain.model.PlayerLevelsState
import fr.nathanaudvard.emp.domain.model.PlayerState
import fr.nathanaudvard.emp.domain.model.WageSettlement
import kotlin.time.Instant

class PlayerService(
    private val exportedPlayers: ExportedPlayerRepository,
    private val players: PlayerRepository,
) {
    suspend fun latestMembers(allianceTag: String): List<AllianceMember> =
        exportedPlayers.latestMembers(allianceTag)

    suspend fun averageFields(
        members: List<AllianceMember>,
        versions: Collection<String>
    ): Map<Long, Double> =
        exportedPlayers.averageFields(members.map { it.id }, versions)

    // A member without saved levels is at level 0 everywhere
    suspend fun levels(members: List<AllianceMember>): Map<Long, PlayerLevels> {
        val saved = players.levels(members.map { it.id })
        return members.associate { it.id to (saved[it.id] ?: PlayerLevels()) }
    }

    suspend fun saveLevel(playerId: Long, kind: PlayerLevel, level: Long, by: Long, at: Instant): LevelChange? =
        players.saveLevel(playerId, kind, level, by, at)

    suspend fun confirmLevels(playerId: Long, by: Long, at: Instant) = players.confirmLevels(playerId, by, at)

    suspend fun levelChange(id: Long): LevelChange? = players.levelChange(id)

    suspend fun levelsState(playerId: Long): PlayerLevelsState = players.levelsState(playerId)

    suspend fun lastSeenComputation(playerId: Long): Long? = players.lastSeenComputation(playerId)

    suspend fun setLastSeenComputation(playerId: Long, computationId: Long) =
        players.setLastSeenComputation(playerId, computationId)

    // A member without a saved choice delivers
    suspend fun wageSettlements(members: List<AllianceMember>): Map<Long, WageSettlement> {
        val saved = players.wageSettlements(members.map { it.id })
        return members.associate { it.id to (saved[it.id] ?: WageSettlement.DELIVER) }
    }

    // A member never saved owes nothing
    suspend fun debts(members: List<AllianceMember>): Map<Long, Long> {
        val saved = players.debts(members.map { it.id })
        return members.associate { it.id to (saved[it.id] ?: 0) }
    }

    suspend fun saveWageSettlement(playerId: Long, settlement: WageSettlement, by: Long, at: Instant) =
        players.saveWageSettlement(playerId, settlement, by, at)

    // A member never saved has the defaults: delivers, owes nothing, levels at 0
    suspend fun states(members: List<AllianceMember>): Map<Long, PlayerState> {
        val saved = players.states(members.map { it.id })
        return members.associate { it.id to (saved[it.id] ?: PlayerState()) }
    }
}
