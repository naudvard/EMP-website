package fr.nathanaudvard.emp.domain.model

import kotlin.time.Instant

data class PlayerLevels(
    val incubator: Long = 0,
    val solarium: Long = 0,
    val layingTechnique: Long = 0,
    val aphidFarm: Long = 0,
) {
    val layingTime: Long get() = incubator + solarium + layingTechnique

    operator fun get(kind: PlayerLevel): Long = when (kind) {
        PlayerLevel.INCUBATOR -> incubator
        PlayerLevel.SOLARIUM -> solarium
        PlayerLevel.LAYING_TECHNIQUE -> layingTechnique
        PlayerLevel.APHID_FARM -> aphidFarm
    }
}

// When the levels were last saved or confirmed unchanged and by whom, null when never
data class PlayerLevelsState(val levels: PlayerLevels, val updatedAt: Instant?, val updatedBy: Long?)

data class LevelChange(
    val id: Long,
    val playerId: Long,
    val kind: PlayerLevel,
    val from: Long,
    val to: Long,
    val changedBy: Long,
    val changedAt: Instant,
)
