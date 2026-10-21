package fr.nathanaudvard.emp.database

import fr.nathanaudvard.emp.db.EmpDatabase
import fr.nathanaudvard.emp.db.Wage_deliveries
import fr.nathanaudvard.emp.db.Wages
import fr.nathanaudvard.emp.domain.model.WageComputation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

class WageRepository(database: EmpDatabase) {
    private val queries = database.wageQueries

    suspend fun save(computation: WageComputation) = withContext(Dispatchers.IO) {
        queries.transaction {
            val id = with(computation) {
                queries.insertComputation(
                    allianceTag, computedAt.toString(), exportVersion, hours.toLong(), distribution.name.lowercase(),
                ).executeAsOne()
            }
            computation.wages.forEach {
                queries.insertWage(
                    Wages(
                        computation_id = id,
                        player_id = it.playerId,
                        pseudo = it.pseudo,
                        average_field = it.averageField,
                        self_production = it.selfProduction,
                        laying_time = it.layingTime,
                        theoretical_need = it.theoreticalNeed,
                        build_time_seconds = it.buildTimeSeconds,
                        reduction_factor = it.reductionFactor,
                        relative_need = it.relativeNeed,
                        gross_wage = it.grossWage,
                        net_wage = it.netWage,
                    ),
                )
            }
            computation.deliveries.forEach {
                queries.insertDelivery(Wage_deliveries(id, it.fromPlayerId, it.toPlayerId, it.amount))
            }
        }
    }
}
