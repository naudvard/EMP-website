package fr.nathanaudvard.emp.domain.usecase

import fr.nathanaudvard.emp.domain.model.AllianceMember
import fr.nathanaudvard.emp.domain.model.PlayerLevels
import fr.nathanaudvard.emp.domain.model.Wage
import fr.nathanaudvard.emp.domain.model.WageDelivery
import fr.nathanaudvard.emp.domain.model.WageDistribution
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.pow

private const val WOOD_PER_FIELD_PER_DAY = 48.0
private const val SECONDS_PER_DAY = 86_400.0

internal data class WageInput(
    val member: AllianceMember,
    val averageField: Double,
    val levels: PlayerLevels,
)

internal fun colonyShare(aphidFarmLevel: Long): Double = (20 + aphidFarmLevel) / 100.0

internal fun theoreticalNeed(layingTime: Long): Double = 10_000 * 2.0.pow(layingTime / 3.0)

// Only the first two tiers are known: x1.7 per level up to level 20, then x1.3
internal fun layingTechniqueResearchSeconds(level: Long): Double =
    if (level <= 20) 120 * 1.7.pow((level - 1).toDouble())
    else layingTechniqueResearchSeconds(20) * 1.3.pow((level - 20).toDouble())

internal fun architectureReduction(level: Long): Double = 0.9.pow(level.toDouble())

internal fun calculateWages(inputs: List<WageInput>): List<Wage> {
    val byId = inputs.associateBy { it.member.id }
    val baseProduction = inputs.associate { it.member.id to it.averageField * WOOD_PER_FIELD_PER_DAY }
    val selfProduction = baseProduction.toMutableMap()
    for (colony in inputs) {
        val master = byId[colony.member.masterPlayerId] ?: continue
        val share = baseProduction.getValue(colony.member.id) * colonyShare(master.levels.aphidFarm)
        selfProduction[colony.member.id] = selfProduction.getValue(colony.member.id) - share
        selfProduction[master.member.id] = selfProduction.getValue(master.member.id) + share
    }

    val partial = inputs.map { input ->
        val layingTechnique = input.levels.layingTechnique
        val theoreticalNeed = floor(theoreticalNeed(input.levels.layingTime)).toLong()
        // The reference is the next level to research
        val buildTimeSeconds = layingTechniqueResearchSeconds(layingTechnique + 1)
        val reductionFactor = architectureReduction(layingTechnique)
        Wage(
            playerId = input.member.id,
            pseudo = input.member.pseudo,
            averageField = input.averageField,
            selfProduction = floor(selfProduction.getValue(input.member.id)).toLong(),
            layingTime = input.levels.layingTime,
            theoreticalNeed = theoreticalNeed,
            buildTimeSeconds = buildTimeSeconds,
            reductionFactor = reductionFactor,
            relativeNeed = floor(theoreticalNeed / (buildTimeSeconds / SECONDS_PER_DAY * reductionFactor)).toLong(),
            grossWage = 0,
            netWage = 0,
        )
    }

    val totalProduction = partial.sumOf { it.selfProduction }.toDouble()
    val totalRelativeNeed = partial.sumOf { it.relativeNeed }.toDouble()
    return partial.map {
        val grossWage = floor(it.relativeNeed * totalProduction / totalRelativeNeed).toLong()
        it.copy(grossWage = grossWage, netWage = grossWage - it.selfProduction)
    }
}

// Rounding down leaves the granaries a few units more than the receivers need: the remainder stays with them
internal fun distribute(wages: List<Wage>, distribution: WageDistribution): List<WageDelivery> {
    val granaries = wages.filter { it.netWage < 0 }.map { it.playerId to -it.netWage }
    val receivers = wages.filter { it.netWage > 0 }.map { it.playerId to it.netWage }
    val deliveries = when (distribution) {
        WageDistribution.PROPORTIONAL -> {
            val totalSurplus = granaries.sumOf { it.second }.toDouble()
            granaries.flatMap { (granary, surplus) ->
                receivers.map { (receiver, need) -> WageDelivery(granary, receiver, floor(need * surplus / totalSurplus).toLong()) }
            }
        }
        WageDistribution.MINIMAL -> minimalDeliveries(granaries, receivers)
    }
    return deliveries.filter { it.amount > 0 }
}

// Largest surpluses go to largest needs, so that each step settles at least one of the two
private fun minimalDeliveries(granaries: List<Pair<Long, Long>>, receivers: List<Pair<Long, Long>>): List<WageDelivery> {
    val givers = granaries.sortedByDescending { it.second }.toMutableList()
    val takers = receivers.sortedByDescending { it.second }.toMutableList()
    val deliveries = mutableListOf<WageDelivery>()
    var g = 0
    var t = 0
    while (g < givers.size && t < takers.size) {
        val amount = min(givers[g].second, takers[t].second)
        deliveries += WageDelivery(givers[g].first, takers[t].first, amount)
        givers[g] = givers[g].first to givers[g].second - amount
        takers[t] = takers[t].first to takers[t].second - amount
        if (givers[g].second == 0L) g++
        if (takers[t].second == 0L) t++
    }
    return deliveries
}
