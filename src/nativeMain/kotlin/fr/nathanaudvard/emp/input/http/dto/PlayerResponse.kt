package fr.nathanaudvard.emp.input.http.dto

import fr.nathanaudvard.emp.domain.model.Player
import kotlinx.serialization.Serializable

@Serializable
data class PlayerResponse(
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

fun Player.toResponse() = PlayerResponse(
    id = id,
    version = version,
    pseudo = pseudo,
    alliance = alliance,
    masterPlayerId = masterPlayerId,
    x = x,
    y = y,
    field = field,
    grade = grade,
    buildingScore = buildingScore,
    technologyScore = technologyScore,
    trophyScore = trophyScore,
    onHoliday = onHoliday,
    isBanned = isBanned,
)
