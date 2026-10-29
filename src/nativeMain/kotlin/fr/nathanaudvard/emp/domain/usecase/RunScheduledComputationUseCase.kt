package fr.nathanaudvard.emp.domain.usecase

import fr.nathanaudvard.emp.service.AllianceSettingsService
import fr.nathanaudvard.emp.service.WageService
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

// The computation of the time set. Null when one was made within the minute: a restart or a second instance must
// not compute twice. A failure is kept for the chiefs until a computation succeeds
class RunScheduledComputationUseCase(
    private val computeNow: ComputeWagesNowUseCase,
    private val wages: WageService,
    private val settings: AllianceSettingsService,
    private val clock: Clock,
) {
    suspend operator fun invoke(allianceTag: String): ComputationResult? {
        val now = clock.now()
        val latest = wages.bounds(allianceTag).latestComputedAt
        if (latest != null && now - latest < 1.minutes) return null
        val result = computeNow(allianceTag)
        when (result) {
            is ComputationResult.Computed -> settings.clearFailure(allianceTag)
            is ComputationResult.Failed -> settings.saveFailure(allianceTag, result.failure, now)
        }
        return result
    }
}
