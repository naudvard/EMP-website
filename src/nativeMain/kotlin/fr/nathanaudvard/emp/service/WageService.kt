package fr.nathanaudvard.emp.service

import fr.nathanaudvard.emp.database.WageRepository
import fr.nathanaudvard.emp.domain.model.AllianceMember
import fr.nathanaudvard.emp.domain.model.LayingNeed
import fr.nathanaudvard.emp.domain.model.PlayerLevels
import fr.nathanaudvard.emp.domain.model.SelfProduction
import fr.nathanaudvard.emp.domain.model.SettledWage
import fr.nathanaudvard.emp.domain.model.Wage
import fr.nathanaudvard.emp.domain.model.WageComputation
import fr.nathanaudvard.emp.domain.model.WageDelivery
import fr.nathanaudvard.emp.domain.model.WageDistribution
import fr.nathanaudvard.emp.domain.model.WageHistoryBounds
import fr.nathanaudvard.emp.domain.model.WageSettlement
import kotlin.math.floor
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class WageService(private val repository: WageRepository) {
    // Production over the period
    fun selfProductions(
        members: List<AllianceMember>,
        averageFields: Map<Long, Double>,
        levels: Map<Long, PlayerLevels>,
        period: Duration,
    ): Map<Long, SelfProduction> =
        selfProductions(members, averageFields, levels.mapValues { it.value.aphidFarm }, period / 1.days)

    fun layingNeeds(levels: Map<Long, PlayerLevels>): Map<Long, LayingNeed> =
        levels.mapValues { (playerId, levels) -> layingNeed(playerId, levels) }

    // The whole alliance production is shared in proportion to the relative needs
    fun wages(
        members: List<AllianceMember>,
        averageFields: Map<Long, Double>,
        selfProductions: Map<Long, SelfProduction>,
        needs: Map<Long, LayingNeed>,
    ): List<Wage> {
        val totalProduction = selfProductions.values.sumOf { it.selfProduction }.toDouble()
        // Summed as Double: a few large needs would overflow Long
        val totalRelativeNeed = needs.values.sumOf { it.relativeNeed.toDouble() }
        return members.map { member ->
            val need = needs.getValue(member.id)
            val production = selfProductions.getValue(member.id)
            val selfProduction = production.selfProduction
            val grossWage = floor(need.relativeNeed * totalProduction / totalRelativeNeed).toLong()
            Wage(
                playerId = member.id,
                pseudo = member.pseudo,
                averageField = averageFields.getValue(member.id),
                selfProduction = selfProduction,
                layingTime = need.layingTime,
                theoreticalNeed = need.theoreticalNeed,
                buildTimeSeconds = need.buildTimeSeconds,
                reductionFactor = need.reductionFactor,
                relativeNeed = need.relativeNeed,
                grossWage = grossWage,
                netWage = grossWage - selfProduction,
                baseProduction = production.baseProduction,
                colonyTransfer = production.colonyTransfer,
                masterPlayerId = production.masterPlayerId,
            )
        }
    }

    fun settleDebts(
        wages: List<Wage>,
        settlements: Map<Long, WageSettlement>,
        debts: Map<Long, Long>,
    ): List<SettledWage> =
        wages.map { settleDebt(it, settlements.getValue(it.playerId), debts.getValue(it.playerId)) }

    fun deliveries(wages: List<SettledWage>, distribution: WageDistribution): List<WageDelivery> =
        distribute(wages, distribution)

    suspend fun save(computation: WageComputation) = repository.save(computation)

    suspend fun bounds(allianceTag: String): WageHistoryBounds = repository.bounds(allianceTag)

    suspend fun olderId(allianceTag: String, id: Long): Long? = repository.olderId(allianceTag, id)

    suspend fun newerId(allianceTag: String, id: Long): Long? = repository.newerId(allianceTag, id)

    suspend fun latestIdBefore(allianceTag: String, instant: Instant): Long? =
        repository.latestIdBefore(allianceTag, instant)

    suspend fun computation(allianceTag: String, id: Long): WageComputation? =
        repository.computation(allianceTag, id)

    suspend fun saveConvoys(computationId: Long, sentAt: Instant, convoys: List<WageDelivery>) =
        repository.saveConvoys(computationId, sentAt, convoys)

    suspend fun deleteConvoy(id: Long, fromPlayerId: Long) = repository.deleteConvoy(id, fromPlayerId)
}
