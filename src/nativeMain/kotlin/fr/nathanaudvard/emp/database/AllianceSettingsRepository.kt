package fr.nathanaudvard.emp.database

import fr.nathanaudvard.emp.db.EmpDatabase
import fr.nathanaudvard.emp.domain.model.AllianceSettings
import fr.nathanaudvard.emp.domain.model.WageDistribution
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalTime
import kotlin.time.Instant

class AllianceSettingsRepository(database: EmpDatabase) {
    private val queries = database.allianceSettingsQueries

    suspend fun settings(allianceTag: String): AllianceSettings =
        withContext(Dispatchers.IO) {
            val row = queries.selectSettings(allianceTag).executeAsOneOrNull() ?: return@withContext AllianceSettings()
            AllianceSettings(
                computationTime = LocalTime.parse(row.computation_time),
                distribution = WageDistribution.valueOf(row.distribution),
                updatedAt = row.updated_at?.let(Instant::parse),
                updatedBy = row.updated_by,
            )
        }

    suspend fun save(allianceTag: String, time: LocalTime, distribution: WageDistribution, by: Long, at: Instant) =
        withContext(Dispatchers.IO) {
            queries.upsertSettings(allianceTag, time.toString(), distribution.name, at.toString(), by)
        }
}
