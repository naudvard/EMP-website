package fr.nathanaudvard.emp.domain.usecase

import fr.nathanaudvard.emp.domain.exception.AllianceNotFoundException
import fr.nathanaudvard.emp.domain.exception.ExportNotFoundException
import fr.nathanaudvard.emp.domain.model.ExportVersion
import fr.nathanaudvard.emp.domain.model.WageComputation
import fr.nathanaudvard.emp.domain.model.WageDistribution
import fr.nathanaudvard.emp.service.ExportService
import fr.nathanaudvard.emp.service.PlayerService
import fr.nathanaudvard.emp.service.WageService
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

class ComputeWagesUseCase(
    private val exports: ExportService,
    private val players: PlayerService,
    private val wages: WageService,
    private val clock: Clock,
) {
    suspend operator fun invoke(
        allianceTag: String,
        distribution: WageDistribution
    ): WageComputation {
        val computedAt = clock.now()
        val since = computedAt - 24.hours

        val syncedPlayers = exports.syncPlayersSince(since)

        val measures = exports.latestOfEachHour(syncedPlayers)
        if (measures.isEmpty()) throw ExportNotFoundException(
            "No players export since ${
                ExportVersion.of(
                    since
                )
            }"
        )

        val members = players.latestMembers(allianceTag)
        if (members.isEmpty()) throw AllianceNotFoundException(allianceTag)

        val averageFields = players.averageFields(members, measures)
        val levels = players.levels(members)

        val selfProductions = wages.selfProductions(members, averageFields, levels)
        val needs = wages.layingNeeds(levels)

        val memberWages = wages.wages(members, averageFields, selfProductions, needs)

        val computation = WageComputation(
            allianceTag = allianceTag,
            computedAt = computedAt,
            exportVersion = measures.max(),
            hours = measures.size,
            distribution = distribution,
            wages = memberWages,
            deliveries = wages.deliveries(memberWages, distribution),
        )
        wages.save(computation)
        return computation
    }
}
