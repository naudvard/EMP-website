package fr.nathanaudvard.emp.domain.model

data class PlayerLevels(
    val incubator: Long = 0,
    val solarium: Long = 0,
    val layingTechnique: Long = 0,
    val aphidFarm: Long = 0,
) {
    val layingTime: Long get() = incubator + solarium + layingTechnique
}
