package fr.nathanaudvard.emp.domain.model

import kotlin.time.Instant

enum class WageDistribution {
    MINIMAL,
    PROPORTIONAL,
}

data class WageComputation(
    val allianceTag: String,
    val computedAt: Instant,
    val exportVersion: String,
    val hours: Int,
    val distribution: WageDistribution,
    val wages: List<Wage>,
    val deliveries: List<WageDelivery>,
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

data class WageDelivery(
    val fromPlayerId: Long,
    val toPlayerId: Long,
    val amount: Long,
)
