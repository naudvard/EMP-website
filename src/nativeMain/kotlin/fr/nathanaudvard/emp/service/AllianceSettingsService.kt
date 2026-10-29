package fr.nathanaudvard.emp.service

import fr.nathanaudvard.emp.database.AllianceSettingsRepository
import fr.nathanaudvard.emp.domain.model.AllianceSettings
import fr.nathanaudvard.emp.domain.model.ComputationFailure
import fr.nathanaudvard.emp.domain.model.WageDistribution
import kotlinx.datetime.LocalTime
import kotlin.time.Instant

class AllianceSettingsService(private val repository: AllianceSettingsRepository) {
    suspend fun settings(allianceTag: String): AllianceSettings = repository.settings(allianceTag)

    suspend fun save(allianceTag: String, time: LocalTime, distribution: WageDistribution, by: Long, at: Instant) =
        repository.save(allianceTag, time, distribution, by, at)

    suspend fun saveFailure(allianceTag: String, failure: ComputationFailure, at: Instant) =
        repository.saveFailure(allianceTag, failure, at)

    suspend fun clearFailure(allianceTag: String) = repository.clearFailure(allianceTag)
}
