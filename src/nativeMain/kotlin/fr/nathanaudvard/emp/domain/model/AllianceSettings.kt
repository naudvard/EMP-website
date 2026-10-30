package fr.nathanaudvard.emp.domain.model

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalTime
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

// The daily computation, at a time of the game: the cron calling the API must run at computationTime.
// UpdatedAt and updatedBy are null until a chief saves them
data class AllianceSettings(
    val computationTime: LocalTime = DEFAULT_COMPUTATION_TIME,
    val distribution: WageDistribution = WageDistribution.MINIMAL,
    val updatedAt: Instant? = null,
    val updatedBy: Long? = null,
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
