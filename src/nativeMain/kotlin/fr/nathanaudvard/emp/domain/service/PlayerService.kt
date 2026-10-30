package fr.nathanaudvard.emp.domain.service

import fr.nathanaudvard.emp.database.AllianceSettingsRepository
import fr.nathanaudvard.emp.database.PlayerRepository
import fr.nathanaudvard.emp.domain.helper.WageHelper
import fr.nathanaudvard.emp.domain.model.LevelChange
import fr.nathanaudvard.emp.domain.model.MyOverview
import fr.nathanaudvard.emp.domain.model.PlayerLevel
import kotlin.time.Clock

class PlayerService(
    private val players: PlayerRepository,
    private val wages: WageHelper,
    private val settings: AllianceSettingsRepository,
    private val clock: Clock,
) {
    // The latest computation counts as seen once the page is shown
    suspend fun overview(allianceTag: String, playerId: Long, changeId: Long? = null): MyOverview {
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

    // By is the player or a chief. Null when the level was already the one saved
    suspend fun saveLevel(playerId: Long, kind: PlayerLevel, level: Long, by: Long): LevelChange? =
        players.saveLevel(playerId, kind, level, by, clock.now())

    // The levels did not change: only their date is renewed
    suspend fun confirmLevels(playerId: Long, by: Long) = players.confirmLevels(playerId, by, clock.now())

    // Puts the former level back, as long as the level is still the one the change saved
    suspend fun undoLevelChange(changeId: Long, playerId: Long, by: Long) {
        val change = players.levelChange(changeId)?.takeIf { it.playerId == playerId } ?: return
        if (players.levelsState(playerId).levels[change.kind] != change.to) return
        players.saveLevel(playerId, change.kind, change.from, by, clock.now())
    }
}
