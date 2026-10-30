package fr.nathanaudvard.emp.domain.service

import fr.nathanaudvard.emp.database.AllianceSettingsRepository
import fr.nathanaudvard.emp.database.ExportedPlayerRepository
import fr.nathanaudvard.emp.database.WageRepository
import fr.nathanaudvard.emp.domain.exception.AllianceNotFoundException
import fr.nathanaudvard.emp.domain.exception.ExportNotFoundException
import fr.nathanaudvard.emp.domain.helper.ExportHelper
import fr.nathanaudvard.emp.domain.helper.PlayerHelper
import fr.nathanaudvard.emp.domain.helper.WageHelper
import fr.nathanaudvard.emp.domain.model.AllianceSettings
import fr.nathanaudvard.emp.domain.model.ExportVersion
import fr.nathanaudvard.emp.domain.model.WageComputation
import fr.nathanaudvard.emp.domain.model.WageDistribution
import fr.nathanaudvard.emp.domain.model.WageHistory
import fr.nathanaudvard.emp.domain.model.WagesPanel
import kotlinx.datetime.LocalTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class WageService(
    private val exports: ExportHelper,
    private val exportedPlayers: ExportedPlayerRepository,
    private val players: PlayerHelper,
    private val wages: WageHelper,
    private val repository: WageRepository,
    private val settings: AllianceSettingsRepository,
    private val alliance: AllianceService,
    private val clock: Clock,
) {
    suspend fun compute(allianceTag: String, distribution: WageDistribution): WageComputation {
        // The period starts at the previous computation, so that each production is shared once
        val computedAt = clock.now()
        val since = repository.bounds(allianceTag).latestComputedAt ?: (computedAt - FIRST_PERIOD)

        val syncedPlayers = exports.syncPlayersSince(since, allianceTag)

        val measures = latestOfEachHour(syncedPlayers)
        if (measures.isEmpty()) throw ExportNotFoundException("No players export since ${ExportVersion.of(since)}")

        val members = exportedPlayers.latestMembers(allianceTag)
        if (members.isEmpty()) throw AllianceNotFoundException(allianceTag)

        val averageFields = exportedPlayers.averageFields(members.map { it.id }, measures)
        val levels = players.levels(members)

        val selfProductions = wages.selfProductions(members, averageFields, levels, computedAt - since)
        val needs = wages.layingNeeds(levels)

        val memberWages = wages.wages(members, averageFields, selfProductions, needs)
        val settledWages = wages.settleDebts(memberWages, players.wageSettlements(members), players.debts(members))
        val settledAmounts = settledWages.associate { it.playerId to it.amount }

        val computation = WageComputation(
            allianceTag = allianceTag,
            since = since,
            computedAt = computedAt,
            exportVersion = measures.max(),
            hours = measures.size,
            distribution = distribution,
            wages = memberWages.map { it.copy(settledAmount = settledAmounts.getValue(it.playerId)) },
            deliveries = wages.deliveries(settledWages, distribution),
            debts = settledWages.associate { it.playerId to it.debt },
        )
        repository.save(computation)
        return computation
    }

    // Selects the given computation, else the latest one computed before the given instant, else the latest one
    suspend fun history(allianceTag: String, computationId: Long? = null, before: Instant? = null): WageHistory {
        val bounds = repository.bounds(allianceTag)
        val selectedId = when {
            computationId != null -> computationId
            before != null -> repository.latestIdBefore(allianceTag, before)
            else -> bounds.latestId
        }
        val selected = selectedId?.let { repository.computation(allianceTag, it) }
        return WageHistory(
            bounds = bounds,
            selectedId = selectedId,
            selected = selected,
            olderId = selected?.let { repository.olderId(allianceTag, selectedId) },
            newerId = selected?.let { repository.newerId(allianceTag, selectedId) },
        )
    }

    suspend fun panel(allianceTag: String): WagesPanel {
        val settings = settings(allianceTag)
        return WagesPanel(settings, settings.nextComputation(clock.now()), alliance.overview(allianceTag))
    }

    suspend fun settings(allianceTag: String): AllianceSettings = settings.settings(allianceTag)

    // By is the chief who set them. The next computation follows them at once
    suspend fun saveSettings(allianceTag: String, time: LocalTime, distribution: WageDistribution, by: Long) =
        settings.save(allianceTag, time, distribution, by, clock.now())
}

// The first computation of an alliance has no previous one to start from
private val FIRST_PERIOD = 24.hours

private fun latestOfEachHour(versions: List<String>): List<String> = versions.groupBy { it.take(10) }
    .values
    .map { it.max() }
