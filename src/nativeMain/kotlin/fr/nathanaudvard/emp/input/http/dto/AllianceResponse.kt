package fr.nathanaudvard.emp.input.http.dto

import fr.nathanaudvard.emp.domain.model.Alliance
import fr.nathanaudvard.emp.domain.model.Pact
import kotlinx.serialization.Serializable

@Serializable
data class AllianceResponse(
    val id: Long,
    val version: String,
    val tag: String,
    val name: String,
    val playersCount: Long,
    val totalField: Long,
    val totalBuildingScore: Long,
    val totalTechnologyScore: Long,
    val totalTrophyScore: Long,
    val wars: List<String>,
    val pacts: List<PactResponse>,
)

@Serializable
data class PactResponse(
    val id: Long,
    val tag: String,
    val name: String,
    val description: String,
)

fun Alliance.toResponse() = AllianceResponse(
    id = id,
    version = version,
    tag = tag,
    name = name,
    playersCount = playersCount,
    totalField = totalField,
    totalBuildingScore = totalBuildingScore,
    totalTechnologyScore = totalTechnologyScore,
    totalTrophyScore = totalTrophyScore,
    wars = wars,
    pacts = pacts.map { it.toResponse() },
)

fun Pact.toResponse() = PactResponse(id = id, tag = tag, name = name, description = description)
