package fr.nathanaudvard.emp.domain.usecase

import fr.nathanaudvard.emp.domain.model.MyOverview
import fr.nathanaudvard.emp.service.AllianceSettingsService
import fr.nathanaudvard.emp.service.PlayerService
import fr.nathanaudvard.emp.service.WageService
import kotlin.time.Clock

// The latest computation counts as seen once the page is shown
class GetMyOverviewUseCase(
    private val wages: WageService,
    private val players: PlayerService,
    private val settings: AllianceSettingsService,
    private val clock: Clock,
) {
    suspend operator fun invoke(allianceTag: String, playerId: Long, changeId: Long? = null): MyOverview {
        val (latest, previous) = wages.latestTwo(allianceTag)
        val lastSeen = players.lastSeenComputation(playerId)
        if (latest != null && lastSeen != latest.id) players.setLastSeenComputation(playerId, latest.id)
        return MyOverview(
            latest = latest,
            previous = previous,
            levels = players.levelsState(playerId),
            change = changeId?.let { players.levelChange(it) }?.takeIf { it.playerId == playerId },
            newComputation = latest != null && (lastSeen == null || lastSeen < latest.id),
            nextComputation = settings.settings(allianceTag).nextComputation(clock.now()),
        )
    }
}
