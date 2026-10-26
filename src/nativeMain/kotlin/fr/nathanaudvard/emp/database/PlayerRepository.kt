package fr.nathanaudvard.emp.database

import fr.nathanaudvard.emp.db.EmpDatabase
import fr.nathanaudvard.emp.domain.model.PlayerLevel
import fr.nathanaudvard.emp.domain.model.PlayerLevels
import fr.nathanaudvard.emp.domain.model.WageSettlement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

class PlayerRepository(database: EmpDatabase) {
    private val queries = database.playerQueries

    // Members deliver until another choice is saved
    suspend fun createMembers(playerIds: Collection<Long>) =
        withContext(Dispatchers.IO) {
            queries.transaction {
                playerIds.forEach { queries.insertMember(it, WageSettlement.DELIVER.name) }
            }
        }

    suspend fun saveLevel(playerId: Long, kind: PlayerLevel, level: Long) =
        withContext(Dispatchers.IO) {
            queries.upsertLevels(
                id = playerId,
                incubator_level = level.takeIf { kind == PlayerLevel.INCUBATOR },
                solarium_level = level.takeIf { kind == PlayerLevel.SOLARIUM },
                laying_technique_level = level.takeIf { kind == PlayerLevel.LAYING_TECHNIQUE },
                aphid_farm_level = level.takeIf { kind == PlayerLevel.APHID_FARM },
            )
        }

    suspend fun levels(playerIds: Collection<Long>): Map<Long, PlayerLevels> =
        withContext(Dispatchers.IO) {
            queries.selectByIds(playerIds).executeAsList().associate {
                it.id to PlayerLevels(
                    incubator = it.incubator_level ?: 0,
                    solarium = it.solarium_level ?: 0,
                    layingTechnique = it.laying_technique_level ?: 0,
                    aphidFarm = it.aphid_farm_level ?: 0,
                )
            }
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

    suspend fun saveWageSettlements(settlements: Map<Long, WageSettlement>) =
        withContext(Dispatchers.IO) {
            queries.transaction {
                settlements.forEach { (playerId, settlement) -> queries.upsertWageSettlement(playerId, settlement.name) }
            }
        }
}
