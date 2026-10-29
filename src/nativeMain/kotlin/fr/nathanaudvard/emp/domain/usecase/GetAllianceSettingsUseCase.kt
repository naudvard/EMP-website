package fr.nathanaudvard.emp.domain.usecase

import fr.nathanaudvard.emp.domain.model.AllianceSettings
import fr.nathanaudvard.emp.service.AllianceSettingsService

class GetAllianceSettingsUseCase(private val settings: AllianceSettingsService) {
    suspend operator fun invoke(allianceTag: String): AllianceSettings = settings.settings(allianceTag)
}
