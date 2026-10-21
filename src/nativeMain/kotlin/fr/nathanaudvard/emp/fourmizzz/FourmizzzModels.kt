package fr.nathanaudvard.emp.fourmizzz

import fr.nathanaudvard.emp.domain.model.Alliance
import fr.nathanaudvard.emp.domain.model.ExportType
import fr.nathanaudvard.emp.domain.model.Pact
import fr.nathanaudvard.emp.domain.model.Player
import kotlinx.serialization.Serializable

@Serializable
data class ExportVersionsRes(
    val players: List<String>,
    val alliances: List<String>,
) {
    operator fun get(type: ExportType): List<String> = when (type) {
        ExportType.PLAYERS -> players
        ExportType.ALLIANCES -> alliances
    }
}

@Serializable
data class PactRes(
    val tag: String,
    val name: String,
    val description: String,
)

@Serializable
data class DiplomacyRes(
    val pacts: List<PactRes>,
    val wars: List<String>,
)

@Serializable
data class AllianceRes(
    val tag: String,
    val name: String,
    val playersCount: Long,
    val totalField: Long,
    val totalBuildingScore: Long,
    val totalTechnologyScore: Long,
    val totalTrophyScore: Long,
    val diplomacy: DiplomacyRes,
)

@Serializable
data class PlayerRes(
    val id: Long,
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

fun AllianceRes.toAlliance() = Alliance(
    tag = tag,
    name = name,
    playersCount = playersCount,
    totalField = totalField,
    totalBuildingScore = totalBuildingScore,
    totalTechnologyScore = totalTechnologyScore,
    totalTrophyScore = totalTrophyScore,
    wars = diplomacy.wars,
    pacts = diplomacy.pacts.map { Pact(tag = it.tag, name = it.name, description = it.description) },
)

fun PlayerRes.toPlayer() = Player(
    id = id,
    pseudo = pseudo,
    allianceTag = alliance,
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
