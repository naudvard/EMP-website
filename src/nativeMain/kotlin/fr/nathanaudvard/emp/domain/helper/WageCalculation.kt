package fr.nathanaudvard.emp.domain.helper

import fr.nathanaudvard.emp.domain.exception.LevelsTooHighException
import fr.nathanaudvard.emp.domain.model.AllianceMember
import fr.nathanaudvard.emp.domain.model.LayingNeed
import fr.nathanaudvard.emp.domain.model.PlayerLevels
import fr.nathanaudvard.emp.domain.model.SelfProduction
import fr.nathanaudvard.emp.domain.model.SettledWage
import fr.nathanaudvard.emp.domain.model.Wage
import fr.nathanaudvard.emp.domain.model.WageDelivery
import fr.nathanaudvard.emp.domain.model.WageDistribution
import fr.nathanaudvard.emp.domain.model.WageSettlement
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.pow

internal const val WOOD_PER_FIELD_PER_DAY = 48.0
internal const val SECONDS_PER_DAY = 86_400.0

internal const val COLONY_BASE_SHARE_PERCENT = 20
internal const val COLONY_SHARE_PERCENT_PER_APHID_FARM_LEVEL = 1

internal fun colonyShare(aphidFarmLevel: Long): Double =
    (COLONY_BASE_SHARE_PERCENT + COLONY_SHARE_PERCENT_PER_APHID_FARM_LEVEL * aphidFarmLevel) / 100.0

// Each colony gives a share of its production to its master, if the master is in the alliance. The share is rounded
// once, so that what a colony gives is what its master receives
internal fun selfProductions(
    members: List<AllianceMember>,
    averageFields: Map<Long, Double>,
    aphidFarmLevels: Map<Long, Long>,
    days: Double,
): Map<Long, SelfProduction> {
    val ids = members.map { it.id }.toSet()
    val baseProductions =
        members.associate { it.id to floor(averageFields.getValue(it.id) * WOOD_PER_FIELD_PER_DAY * days).toLong() }
    val masters = members.associate { it.id to it.masterPlayerId?.takeIf { master -> master in ids } }
    val shares = masters.mapValues { (colony, master) ->
        master?.let { floor(baseProductions.getValue(colony) * colonyShare(aphidFarmLevels.getValue(it))).toLong() } ?: 0
    }
    return members.associate { member ->
        val received = masters.filterValues { it == member.id }.keys.sumOf { shares.getValue(it) }
        member.id to SelfProduction(
            baseProduction = baseProductions.getValue(member.id),
            colonyTransfer = received - shares.getValue(member.id),
            masterPlayerId = masters.getValue(member.id),
        )
    }
}

internal fun theoreticalNeed(layingTime: Long): Double = 10_000 * 2.0.pow(layingTime / 3.0)

// Only the first two tiers are known: x1.7 per level up to level 20, then x1.3
internal fun layingTechniqueResearchSeconds(level: Long): Double =
    if (level <= 20) 120 * 1.7.pow((level - 1).toDouble())
    else layingTechniqueResearchSeconds(20) * 1.3.pow((level - 20).toDouble())

internal fun architectureReduction(level: Long): Double = 0.9.pow(level.toDouble())

// Levels saved before they were bounded can exceed Long: refused rather than saturated into wrong wages
internal fun layingNeed(playerId: Long, levels: PlayerLevels): LayingNeed {
    fun Double.wholeUnits(): Long {
        if (!isFinite() || this >= Long.MAX_VALUE.toDouble()) throw LevelsTooHighException(playerId)
        return floor(this).toLong()
    }

    val theoreticalNeed = theoreticalNeed(levels.layingTime).wholeUnits()
    // The reference is the next level to research
    val buildTimeSeconds = layingTechniqueResearchSeconds(levels.layingTechnique + 1)
    val reductionFactor = architectureReduction(levels.layingTechnique)
    return LayingNeed(
        layingTime = levels.layingTime,
        theoreticalNeed = theoreticalNeed,
        buildTimeSeconds = buildTimeSeconds,
        reductionFactor = reductionFactor,
        relativeNeed = (theoreticalNeed / (buildTimeSeconds / SECONDS_PER_DAY * reductionFactor)).wholeUnits(),
    )
}

// A receiver repays by not receiving their wage. A giver who delivers repays on top of what they owe,
// one who accumulates delivers nothing and owes it on top of their debt
internal fun settleDebt(wage: Wage, settlement: WageSettlement, debt: Long): SettledWage = when {
    wage.netWage > 0 -> {
        val repaid = min(debt, wage.netWage)
        SettledWage(wage.playerId, wage.netWage, wage.netWage - repaid, debt - repaid)
    }

    settlement == WageSettlement.DELIVER -> SettledWage(wage.playerId, wage.netWage, wage.netWage - debt, 0)
    else -> SettledWage(wage.playerId, wage.netWage, 0, debt - wage.netWage)
}

// Rounding down leaves the granaries a few units more than the receivers need: the remainder stays with them.
// What debts hold back is taken from the receivers, what they repay goes to the receivers not repaying,
// in proportion to what they receive
internal fun distribute(wages: List<SettledWage>, distribution: WageDistribution): List<WageDelivery> {
    val granaries = wages.filter { it.amount < 0 }.map { it.playerId to -it.amount }
    val dues = wages.filter { it.amount > 0 }
    val totalDue = dues.sumOf { it.amount }
    val toShare = (totalDue + wages.sumOf { it.netWage - it.amount }).coerceIn(0, granaries.sumOf { it.second })
    val sharing = if (toShare > totalDue) dues.filter { it.amount == it.netWage } else dues
    val sharingDue = sharing.sumOf { it.amount }
    val sharingIds = sharing.map { it.playerId }.toSet()
    val receivers = dues.map {
        if (it.playerId !in sharingIds || toShare == totalDue) it.playerId to it.amount
        else it.playerId to floor(it.amount.toDouble() * (sharingDue + toShare - totalDue) / sharingDue).toLong()
    }
    val deliveries = when (distribution) {
        WageDistribution.PROPORTIONAL -> proportionalDeliveries(granaries, receivers)
        WageDistribution.MINIMAL -> minimalDeliveries(granaries, receivers)
    }
    return deliveries.filter { it.amount > 0 }
}

private fun proportionalDeliveries(
    granaries: List<Pair<Long, Long>>,
    receivers: List<Pair<Long, Long>>
): List<WageDelivery> {
    val totalSurplus = granaries.sumOf { it.second }.toDouble()
    return granaries.flatMap { (granary, surplus) ->
        receivers.map { (receiver, need) ->
            WageDelivery(
                granary,
                receiver,
                floor(need * surplus / totalSurplus).toLong()
            )
        }
    }
}

// Largest surpluses go to largest needs, so that each step settles at least one of the two
private fun minimalDeliveries(
    granaries: List<Pair<Long, Long>>,
    receivers: List<Pair<Long, Long>>
): List<WageDelivery> {
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
