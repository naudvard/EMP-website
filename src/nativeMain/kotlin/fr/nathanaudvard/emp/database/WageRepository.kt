package fr.nathanaudvard.emp.database

import fr.nathanaudvard.emp.db.EmpDatabase
import fr.nathanaudvard.emp.db.Wage_deliveries
import fr.nathanaudvard.emp.db.Wages
import fr.nathanaudvard.emp.domain.model.Wage
import fr.nathanaudvard.emp.domain.model.WageComputation
import fr.nathanaudvard.emp.domain.model.WageDelivery
import fr.nathanaudvard.emp.domain.model.WageDistribution
import fr.nathanaudvard.emp.domain.model.WageHistoryBounds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlin.time.Instant

class WageRepository(database: EmpDatabase) {
    private val queries = database.wageQueries

    suspend fun save(computation: WageComputation) = withContext(Dispatchers.IO) {
        queries.transaction {
            val id = with(computation) {
                queries.insertComputation(
                    allianceTag,
                    computedAt.toString(),
                    exportVersion,
                    hours.toLong(),
                    distribution.name.lowercase(),
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
                queries.insertDelivery(
                    Wage_deliveries(
                        id,
                        it.fromPlayerId,
                        it.toPlayerId,
                        it.amount
                    )
                )
            }
        }
    }

    suspend fun bounds(allianceTag: String): WageHistoryBounds = withContext(Dispatchers.IO) {
        queries.selectBounds(allianceTag) { latestId, firstComputedAt, latestComputedAt ->
            WageHistoryBounds(latestId, firstComputedAt?.let(Instant::parse), latestComputedAt?.let(Instant::parse))
        }.executeAsOne()
    }

    suspend fun olderId(allianceTag: String, id: Long): Long? = withContext(Dispatchers.IO) {
        queries.selectOlderId(allianceTag, id).executeAsOneOrNull()
    }

    suspend fun newerId(allianceTag: String, id: Long): Long? = withContext(Dispatchers.IO) {
        queries.selectNewerId(allianceTag, id).executeAsOneOrNull()
    }

    suspend fun latestIdBefore(allianceTag: String, instant: Instant): Long? = withContext(Dispatchers.IO) {
        queries.selectLatestIdBefore(allianceTag, instant.toString()).executeAsOneOrNull()
    }

    suspend fun computation(allianceTag: String, id: Long): WageComputation? = withContext(Dispatchers.IO) {
        queries.transactionWithResult {
            val computation = queries.selectComputation(allianceTag, id).executeAsOneOrNull()
                ?: return@transactionWithResult null
            WageComputation(
                allianceTag = computation.alliance_tag,
                computedAt = Instant.parse(computation.computed_at),
                exportVersion = computation.export_version,
                hours = computation.hours.toInt(),
                distribution = computation.distribution.toDistribution(),
                wages = queries.selectWages(computation.id).executeAsList().map { it.toWage() },
                deliveries = queries.selectDeliveries(computation.id).executeAsList().map {
                    WageDelivery(it.from_player_id, it.to_player_id, it.amount)
                },
            )
        }
    }
}

private fun String.toDistribution() = WageDistribution.valueOf(uppercase())

private fun Wages.toWage() = Wage(
    playerId = player_id,
    pseudo = pseudo,
    averageField = average_field,
    selfProduction = self_production,
    layingTime = laying_time,
    theoreticalNeed = theoretical_need,
    buildTimeSeconds = build_time_seconds,
    reductionFactor = reduction_factor,
    relativeNeed = relative_need,
    grossWage = gross_wage,
    netWage = net_wage,
)
