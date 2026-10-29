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

    // Computations made before the detail was stored have none
    val hasDetail: Boolean get() = wages.all { it.baseProduction != null }

    // Share of the alliance needs, from the needs stored with the computation: the levels may have changed since
    fun needShare(playerId: Long): Double =
        wages.single { it.playerId == playerId }.relativeNeed / wages.sumOf { it.relativeNeed.toDouble() }

    fun colonies(masterPlayerId: Long): List<Wage> = wages.filter { it.masterPlayerId == masterPlayerId }

    // What the convoys from a player to another add up to
    fun sent(fromPlayerId: Long, toPlayerId: Long): Long =
        convoys.filter { it.fromPlayerId == fromPlayerId && it.toPlayerId == toPlayerId }.sumOf { it.amount }

    // What the convoys sent towards a delivery cover, never more than the delivery
    fun covered(delivery: WageDelivery): Long = minOf(sent(delivery.fromPlayerId, delivery.toPlayerId), delivery.amount)
}

enum class ConvoySource {
    // From the game's message, which gives its times
    PASTED,

    // Sent without a message: the rest of a delivery
    DECLARED,
}

data class Convoy(
    val id: Long,
    val fromPlayerId: Long,
    val toPlayerId: Long,
    val amount: Long,
    // When it was recorded
    val sentAt: Instant,
    val source: ConvoySource = ConvoySource.PASTED,
    val arrivesAt: Instant? = null,
    val returnAt: Instant? = null,
    // Who recorded it, null for the convoys recorded before it was stored
    val declaredBy: Long? = null,
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
    // Detail, null for the computations made before it was stored
    val baseProduction: Long? = null,
    // Given to the master (negative) or received from the colonies (positive)
    val colonyTransfer: Long? = null,
    // Master in the alliance the player gives to as a colony
    val masterPlayerId: Long? = null,
    // What the player receives (positive) or sends (negative) once their debt is settled
    val settledAmount: Long? = null,
)

data class SelfProduction(
    val baseProduction: Long,
    val colonyTransfer: Long,
    val masterPlayerId: Long?,
) {
    val selfProduction: Long get() = baseProduction + colonyTransfer
}

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

// A computation with the id it was saved under
data class SavedComputation(val id: Long, val computation: WageComputation)
