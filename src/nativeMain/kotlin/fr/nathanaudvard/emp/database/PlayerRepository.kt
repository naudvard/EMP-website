package fr.nathanaudvard.emp.database

import fr.nathanaudvard.emp.db.EmpDatabase
import fr.nathanaudvard.emp.domain.model.LayingTimeFactor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

class PlayerRepository(database: EmpDatabase) {
    private val queries = database.playerQueries

    suspend fun saveLevel(playerId: Long, factor: LayingTimeFactor, level: Long) = withContext(Dispatchers.IO) {
        queries.upsertLevels(
            id = playerId,
            incubator_level = level.takeIf { factor == LayingTimeFactor.INCUBATOR },
            solarium_level = level.takeIf { factor == LayingTimeFactor.SOLARIUM },
            laying_technique_level = level.takeIf { factor == LayingTimeFactor.LAYING_TECHNIQUE },
        )
    }
}
