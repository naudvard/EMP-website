package fr.nathanaudvard.emp.domain.usecase

import fr.nathanaudvard.emp.database.ExportedPlayerRepository
import fr.nathanaudvard.emp.database.PlayerRepository
import fr.nathanaudvard.emp.database.WageRepository
import fr.nathanaudvard.emp.domain.exception.AllianceNotFoundException
import fr.nathanaudvard.emp.domain.exception.ExportNotFoundException
import fr.nathanaudvard.emp.domain.model.ExportType
import fr.nathanaudvard.emp.domain.model.ExportVersion
import fr.nathanaudvard.emp.domain.model.PlayerLevels
import fr.nathanaudvard.emp.domain.model.WageComputation
import fr.nathanaudvard.emp.domain.model.WageDistribution
import fr.nathanaudvard.emp.fourmizzz.FourmizzzClient
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

class ComputeWages(
    private val fourmizzz: FourmizzzClient,
    private val syncPlayers: SyncExportedPlayers,
    private val exportedPlayers: ExportedPlayerRepository,
    private val players: PlayerRepository,
    private val wages: WageRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(allianceTag: String, distribution: WageDistribution): WageComputation {
        val computedAt = clock.now()
        val since = ExportVersion.of(computedAt - 24.hours)

        val available = fourmizzz.versions(ExportType.PLAYERS).filter { it >= since }
        val stored = exportedPlayers.versionsSince(since)
        (available - stored.toSet()).forEach { syncPlayers(ExportVersion.Exact(it)) }

        // One measure per hour: the latest export of each yyyyMMddHH
        val versions = (available + stored).groupBy { it.take(10) }.values.map { it.max() }
        if (versions.isEmpty()) throw ExportNotFoundException("No players export since $since")

        val members = exportedPlayers.latestMembers(allianceTag)
        if (members.isEmpty()) throw AllianceNotFoundException(allianceTag)
        val ids = members.map { it.id }
        val averageFields = exportedPlayers.averageFields(ids, versions)
        val levels = players.levels(ids)

        val computed = calculateWages(
            members.map { WageInput(it, averageFields.getValue(it.id), levels[it.id] ?: PlayerLevels()) },
        )
        val computation = WageComputation(
            allianceTag = allianceTag,
            computedAt = computedAt,
            exportVersion = versions.max(),
            hours = versions.size,
            distribution = distribution,
            wages = computed,
            deliveries = distribute(computed, distribution),
        )
        wages.save(computation)
        return computation
    }
}
