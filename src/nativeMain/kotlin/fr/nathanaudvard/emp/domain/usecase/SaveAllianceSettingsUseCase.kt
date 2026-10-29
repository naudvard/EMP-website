package fr.nathanaudvard.emp.domain.usecase

import fr.nathanaudvard.emp.domain.model.WageDistribution
import fr.nathanaudvard.emp.service.AllianceSettingsService
import kotlinx.datetime.LocalTime
import kotlin.time.Clock

// By is the chief who set them. The next computation follows them at once
class SaveAllianceSettingsUseCase(private val settings: AllianceSettingsService, private val clock: Clock) {
    suspend operator fun invoke(allianceTag: String, time: LocalTime, distribution: WageDistribution, by: Long) =
        settings.save(allianceTag, time, distribution, by, clock.now())
}
