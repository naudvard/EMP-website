package fr.nathanaudvard.emp.service

import fr.nathanaudvard.emp.database.WageRepository
import fr.nathanaudvard.emp.domain.model.AllianceMember
import fr.nathanaudvard.emp.domain.model.LayingNeed
import fr.nathanaudvard.emp.domain.model.PlayerLevels
import fr.nathanaudvard.emp.domain.model.Wage
import fr.nathanaudvard.emp.domain.model.WageComputation
import fr.nathanaudvard.emp.domain.model.WageDelivery
import fr.nathanaudvard.emp.domain.model.WageDistribution
import fr.nathanaudvard.emp.domain.model.WageHistoryBounds
import kotlin.math.floor
import kotlin.time.Instant

class WageService(private val repository: WageRepository) {
    // Each colony gives a share of its production to its master, if the master is in the alliance
    fun selfProductions(
        members: List<AllianceMember>,
        averageFields: Map<Long, Double>,
        levels: Map<Long, PlayerLevels>,
    ): Map<Long, Long> {
        val ids = members.map { it.id }.toSet()
        val baseProduction =
            members.associate { it.id to averageFields.getValue(it.id) * WOOD_PER_FIELD_PER_DAY }
        val selfProduction = baseProduction.toMutableMap()
        for (colony in members) {
            val master = colony.masterPlayerId?.takeIf { it in ids } ?: continue
            val share =
                baseProduction.getValue(colony.id) * colonyShare(levels.getValue(master).aphidFarm)
            selfProduction[colony.id] = selfProduction.getValue(colony.id) - share
            selfProduction[master] = selfProduction.getValue(master) + share
        }
        return selfProduction.mapValues { floor(it.value).toLong() }
    }

    fun layingNeeds(levels: Map<Long, PlayerLevels>): Map<Long, LayingNeed> =
        levels.mapValues { (playerId, levels) -> layingNeed(playerId, levels) }

    // The whole alliance production is shared in proportion to the relative needs
    fun wages(
        members: List<AllianceMember>,
        averageFields: Map<Long, Double>,
        selfProductions: Map<Long, Long>,
        needs: Map<Long, LayingNeed>,
    ): List<Wage> {
        val totalProduction = selfProductions.values.sum().toDouble()
        // Summed as Double: a few large needs would overflow Long
        val totalRelativeNeed = needs.values.sumOf { it.relativeNeed.toDouble() }
        return members.map { member ->
            val need = needs.getValue(member.id)
            val selfProduction = selfProductions.getValue(member.id)
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
            )
        }
    }

    fun deliveries(wages: List<Wage>, distribution: WageDistribution): List<WageDelivery> =
        distribute(wages, distribution)

    suspend fun save(computation: WageComputation) = repository.save(computation)

    suspend fun bounds(allianceTag: String): WageHistoryBounds = repository.bounds(allianceTag)

    suspend fun olderId(allianceTag: String, id: Long): Long? = repository.olderId(allianceTag, id)

    suspend fun newerId(allianceTag: String, id: Long): Long? = repository.newerId(allianceTag, id)

    suspend fun latestIdBefore(allianceTag: String, instant: Instant): Long? =
        repository.latestIdBefore(allianceTag, instant)

    suspend fun computation(allianceTag: String, id: Long): WageComputation? =
        repository.computation(allianceTag, id)
}
