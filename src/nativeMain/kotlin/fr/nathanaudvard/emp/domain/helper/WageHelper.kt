package fr.nathanaudvard.emp.domain.helper

import fr.nathanaudvard.emp.database.WageRepository
import fr.nathanaudvard.emp.domain.model.AllianceMember
import fr.nathanaudvard.emp.domain.model.LayingNeed
import fr.nathanaudvard.emp.domain.model.SavedComputation
import fr.nathanaudvard.emp.domain.model.PlayerLevels
import fr.nathanaudvard.emp.domain.model.SelfProduction
import fr.nathanaudvard.emp.domain.model.SettledWage
import fr.nathanaudvard.emp.domain.model.Wage
import fr.nathanaudvard.emp.domain.model.WageDelivery
import fr.nathanaudvard.emp.domain.model.WageDistribution
import fr.nathanaudvard.emp.domain.model.WageSettlement
import kotlin.math.floor
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

class WageHelper(private val repository: WageRepository) {
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

    suspend fun latest(allianceTag: String): SavedComputation? {
        val latestId = repository.bounds(allianceTag).latestId ?: return null
        return repository.computation(allianceTag, latestId)?.let { SavedComputation(latestId, it) }
    }

    // The latest computation of the alliance and the one before, whose rest can still be sent
    suspend fun latestTwo(allianceTag: String): Pair<SavedComputation?, SavedComputation?> {
        val latestId = repository.bounds(allianceTag).latestId ?: return null to null
        val latest = repository.computation(allianceTag, latestId)?.let { SavedComputation(latestId, it) }
        val previous = repository.olderId(allianceTag, latestId)
            ?.let { id -> repository.computation(allianceTag, id)?.let { SavedComputation(id, it) } }
        return latest to previous
    }
}
