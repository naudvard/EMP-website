package fr.nathanaudvard.emp.domain.model

data class Player(
    val id: Long,
    val version: String,
    val pseudo: String,
    val alliance: String?,
    val masterPlayerId: Long?,
    val x: Long,
    val y: Long,
    val field: Long,
    val grade: String?,
    val buildingScore: Long,
    val technologyScore: Long,
    val trophyScore: Long,
    val onHoliday: Boolean,
    val isBanned: Boolean,
)
