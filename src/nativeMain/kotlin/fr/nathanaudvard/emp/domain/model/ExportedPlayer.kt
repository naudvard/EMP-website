package fr.nathanaudvard.emp.domain.model

data class ExportedPlayer(
    val id: Long,
    val pseudo: String,
    val allianceTag: String?,
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
