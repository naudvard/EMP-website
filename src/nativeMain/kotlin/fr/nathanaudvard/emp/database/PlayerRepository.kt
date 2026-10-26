package fr.nathanaudvard.emp.database

import fr.nathanaudvard.emp.db.EmpDatabase
import fr.nathanaudvard.emp.domain.model.PlayerLevel
import fr.nathanaudvard.emp.domain.model.PlayerLevels
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

class PlayerRepository(database: EmpDatabase) {
    private val queries = database.playerQueries

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
}
