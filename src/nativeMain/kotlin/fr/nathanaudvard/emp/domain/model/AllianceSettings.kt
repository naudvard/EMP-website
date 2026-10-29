package fr.nathanaudvard.emp.domain.model

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalTime
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

// Why a computation could not be made
enum class ComputationFailure {
    // No players export since the previous computation
    NO_EXPORT,

    // No member in the latest players export
    NO_MEMBERS,
    FOURMIZZZ_UNAVAILABLE,
    UNEXPECTED,
}

// The daily computation, at a time of the game. UpdatedAt and updatedBy are null until a chief saves them;
// failedAt and failure tell the latest scheduled computation that failed, until one succeeds
data class AllianceSettings(
    val computationTime: LocalTime = DEFAULT_COMPUTATION_TIME,
    val distribution: WageDistribution = WageDistribution.MINIMAL,
    val updatedAt: Instant? = null,
    val updatedBy: Long? = null,
    val failedAt: Instant? = null,
    val failure: ComputationFailure? = null,
) {
    // The first computation after now. In the game's time zone: the same time of day across a change of offset
    fun nextComputation(now: Instant): Instant {
        val today = now.toLocalDateTime(gameTimeZone).date
        return generateSequence(today) { it.plus(DatePeriod(days = 1)) }
            .map { it.atTime(computationTime).toInstant(gameTimeZone) }
            .first { it > now }
    }
}

val DEFAULT_COMPUTATION_TIME = LocalTime(18, 0)
