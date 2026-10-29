package fr.nathanaudvard.emp.database

import fr.nathanaudvard.emp.db.EmpDatabase
import fr.nathanaudvard.emp.db.Player_level_changes
import fr.nathanaudvard.emp.db.Players
import fr.nathanaudvard.emp.domain.model.LevelChange
import fr.nathanaudvard.emp.domain.model.PlayerLevel
import fr.nathanaudvard.emp.domain.model.PlayerLevels
import fr.nathanaudvard.emp.domain.model.PlayerLevelsState
import fr.nathanaudvard.emp.domain.model.PlayerState
import fr.nathanaudvard.emp.domain.model.WageSettlement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlin.time.Instant

class PlayerRepository(database: EmpDatabase) {
    private val queries = database.playerQueries

    // Members deliver until another choice is saved
    suspend fun createMembers(playerIds: Collection<Long>) =
        withContext(Dispatchers.IO) {
            queries.transaction {
                playerIds.forEach { queries.insertMember(it, WageSettlement.DELIVER.name) }
            }
        }

    // Saving counts as a confirmation of every level. Null when the level was already the one saved
    suspend fun saveLevel(playerId: Long, kind: PlayerLevel, level: Long, by: Long, at: Instant): LevelChange? =
        withContext(Dispatchers.IO) {
            queries.transactionWithResult {
                val old = queries.selectByIds(listOf(playerId)).executeAsOneOrNull()?.levels()?.get(kind) ?: 0
                queries.upsertLevels(
                    id = playerId,
                    incubator_level = level.takeIf { kind == PlayerLevel.INCUBATOR },
                    solarium_level = level.takeIf { kind == PlayerLevel.SOLARIUM },
                    laying_technique_level = level.takeIf { kind == PlayerLevel.LAYING_TECHNIQUE },
                    aphid_farm_level = level.takeIf { kind == PlayerLevel.APHID_FARM },
                )
                queries.touchLevels(playerId, at.toString(), by)
                if (old == level) return@transactionWithResult null
                val id = queries.insertLevelChange(playerId, kind.name, old, level, by, at.toString()).executeAsOne()
                LevelChange(id, playerId, kind, old, level, by, at)
            }
        }

    suspend fun confirmLevels(playerId: Long, by: Long, at: Instant) =
        withContext(Dispatchers.IO) {
            queries.touchLevels(playerId, at.toString(), by)
        }

    suspend fun levelChange(id: Long): LevelChange? =
        withContext(Dispatchers.IO) {
            queries.levelChange(id).executeAsOneOrNull()?.toLevelChange()
        }

    suspend fun levels(playerIds: Collection<Long>): Map<Long, PlayerLevels> =
        withContext(Dispatchers.IO) {
            queries.selectByIds(playerIds).executeAsList().associate { it.id to it.levels() }
        }

    suspend fun levelsState(playerId: Long): PlayerLevelsState =
        withContext(Dispatchers.IO) {
            val row = queries.selectByIds(listOf(playerId)).executeAsOneOrNull()
            PlayerLevelsState(
                levels = row?.levels() ?: PlayerLevels(),
                updatedAt = row?.levels_updated_at?.let(Instant::parse),
                updatedBy = row?.levels_updated_by,
            )
        }

    suspend fun wageSettlements(playerIds: Collection<Long>): Map<Long, WageSettlement> =
        withContext(Dispatchers.IO) {
            queries.selectByIds(playerIds).executeAsList()
                .mapNotNull { row -> row.wage_settlement?.let { row.id to WageSettlement.valueOf(it) } }
                .toMap()
        }

    suspend fun debts(playerIds: Collection<Long>): Map<Long, Long> =
        withContext(Dispatchers.IO) {
            queries.selectByIds(playerIds).executeAsList().associate { it.id to it.debt }
        }

    suspend fun chiefs(playerIds: Collection<Long>): Set<Long> =
        withContext(Dispatchers.IO) {
            queries.selectByIds(playerIds).executeAsList().filter { it.is_chief != 0L }.map { it.id }.toSet()
        }

    suspend fun setChief(playerId: Long, chief: Boolean) =
        withContext(Dispatchers.IO) {
            queries.upsertChief(playerId, if (chief) 1 else 0)
        }

    suspend fun lastSeenComputation(playerId: Long): Long? =
        withContext(Dispatchers.IO) {
            queries.selectByIds(listOf(playerId)).executeAsOneOrNull()?.last_seen_computation_id
        }

    suspend fun setLastSeenComputation(playerId: Long, computationId: Long) =
        withContext(Dispatchers.IO) {
            queries.upsertLastSeenComputation(playerId, computationId)
        }

    suspend fun saveWageSettlement(playerId: Long, settlement: WageSettlement, by: Long, at: Instant) =
        withContext(Dispatchers.IO) {
            queries.upsertWageSettlement(playerId, settlement.name, at.toString(), by)
        }

    // Only the players saved so far
    suspend fun states(playerIds: Collection<Long>): Map<Long, PlayerState> =
        withContext(Dispatchers.IO) {
            queries.selectByIds(playerIds).executeAsList().associate { row ->
                row.id to PlayerState(
                    levels = PlayerLevelsState(
                        levels = row.levels(),
                        updatedAt = row.levels_updated_at?.let(Instant::parse),
                        updatedBy = row.levels_updated_by,
                    ),
                    settlement = row.wage_settlement?.let(WageSettlement::valueOf) ?: WageSettlement.DELIVER,
                    settlementUpdatedAt = row.wage_settlement_updated_at?.let(Instant::parse),
                    settlementUpdatedBy = row.wage_settlement_updated_by,
                    debt = row.debt,
                    chief = row.is_chief != 0L,
                    lastSeenAt = row.last_seen_at?.let(Instant::parse),
                )
            }
        }
}

private fun Players.levels() = PlayerLevels(
    incubator = incubator_level ?: 0,
    solarium = solarium_level ?: 0,
    layingTechnique = laying_technique_level ?: 0,
    aphidFarm = aphid_farm_level ?: 0,
)

private fun Player_level_changes.toLevelChange() = LevelChange(
    id = id,
    playerId = player_id,
    kind = PlayerLevel.valueOf(kind),
    from = old_level,
    to = new_level,
    changedBy = changed_by,
    changedAt = Instant.parse(changed_at),
)
