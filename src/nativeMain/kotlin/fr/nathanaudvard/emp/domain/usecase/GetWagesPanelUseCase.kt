package fr.nathanaudvard.emp.domain.usecase

import fr.nathanaudvard.emp.domain.model.WagesPanel
import fr.nathanaudvard.emp.service.AllianceSettingsService
import kotlin.time.Clock

class GetWagesPanelUseCase(
    private val getAlliance: GetAllianceUseCase,
    private val settings: AllianceSettingsService,
    private val clock: Clock,
) {
    suspend operator fun invoke(allianceTag: String): WagesPanel {
        val settings = settings.settings(allianceTag)
        return WagesPanel(settings, settings.nextComputation(clock.now()), getAlliance(allianceTag))
    }
}
