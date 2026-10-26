package fr.nathanaudvard.emp.domain.model

import kotlin.time.Instant

enum class WageDistribution {
    MINIMAL,
    PROPORTIONAL,
}

data class WageComputation(
    val allianceTag: String,
    // The production shared is the one from this instant to the computation
    val since: Instant,
    val computedAt: Instant,
    val exportVersion: String,
    val hours: Int,
    val distribution: WageDistribution,
    val wages: List<Wage>,
    val deliveries: List<WageDelivery>,
    // Debt of each player once the computation is settled
    val debts: Map<Long, Long>,
    // Convoys launched towards the deliveries, oldest first
    val convoys: List<Convoy> = emptyList(),
) {
    fun wage(pseudo: String): Wage? = wages.find { it.pseudo.equals(pseudo, ignoreCase = true) }

    // What the convoys from a player to another add up to
    fun sent(fromPlayerId: Long, toPlayerId: Long): Long =
        convoys.filter { it.fromPlayerId == fromPlayerId && it.toPlayerId == toPlayerId }.sumOf { it.amount }
}

data class Convoy(
    val id: Long,
    val fromPlayerId: Long,
    val toPlayerId: Long,
    val amount: Long,
    val sentAt: Instant,
)

// Null bounds when the alliance has no computation
data class WageHistoryBounds(
    val latestId: Long?,
    val firstComputedAt: Instant?,
    val latestComputedAt: Instant?,
)

// Selected is null when the alliance has no computation or the requested one is not found.
// Older and newer are the ids of the selected computation's neighbours
data class WageHistory(
    val bounds: WageHistoryBounds,
    val selectedId: Long?,
    val selected: WageComputation?,
    val olderId: Long?,
    val newerId: Long?,
)

data class Wage(
    val playerId: Long,
    val pseudo: String,
    val averageField: Double,
    val selfProduction: Long,
    val layingTime: Long,
    val theoreticalNeed: Long,
    val buildTimeSeconds: Double,
    val reductionFactor: Double,
    val relativeNeed: Long,
    val grossWage: Long,
    // Gross wage minus self production. Positive: the player receives it, negative: the player gives it
    val netWage: Long,
)

// What the player receives (positive) or delivers (negative) once their debt is settled, and the debt left
data class SettledWage(
    val playerId: Long,
    val netWage: Long,
    val amount: Long,
    val debt: Long,
)

data class WageDelivery(
    val fromPlayerId: Long,
    val toPlayerId: Long,
    val amount: Long,
)

data class LayingNeed(
    val layingTime: Long,
    val theoreticalNeed: Long,
    val buildTimeSeconds: Double,
    val reductionFactor: Double,
    val relativeNeed: Long,
)
