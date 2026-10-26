package fr.nathanaudvard.emp.api.dto

import fr.nathanaudvard.emp.domain.model.Wage
import fr.nathanaudvard.emp.domain.model.WageComputation
import kotlinx.serialization.Serializable

@Serializable
data class WageComputationResponse(
    val allianceTag: String,
    val computedAt: String,
    val exportVersion: String,
    val hours: Int,
    val distribution: String,
    val wages: List<WageResponse>,
    val deliveries: List<WageDeliveryResponse>,
)

@Serializable
data class WageResponse(
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
    val netWage: Long,
)

@Serializable
data class WageDeliveryResponse(
    val fromPlayerId: Long,
    val fromPseudo: String,
    val toPlayerId: Long,
    val toPseudo: String,
    val amount: Long,
)

fun WageComputation.toResponse(): WageComputationResponse {
    val pseudos = wages.associate { it.playerId to it.pseudo }
    return WageComputationResponse(
        allianceTag = allianceTag,
        computedAt = computedAt.toString(),
        exportVersion = exportVersion,
        hours = hours,
        distribution = distribution.name.lowercase(),
        wages = wages.map { it.toResponse() },
        deliveries = deliveries.map {
            WageDeliveryResponse(
                it.fromPlayerId,
                pseudos.getValue(it.fromPlayerId),
                it.toPlayerId,
                pseudos.getValue(it.toPlayerId),
                it.amount
            )
        },
    )
}

fun Wage.toResponse() = WageResponse(
    playerId = playerId,
    pseudo = pseudo,
    averageField = averageField,
    selfProduction = selfProduction,
    layingTime = layingTime,
    theoreticalNeed = theoreticalNeed,
    buildTimeSeconds = buildTimeSeconds,
    reductionFactor = reductionFactor,
    relativeNeed = relativeNeed,
    grossWage = grossWage,
    netWage = netWage,
)
