package fr.nathanaudvard.emp.domain.model

data class Alliance(
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
    val pacts: List<Pact>,
)

data class Pact(
    val id: Long,
    val tag: String,
    val name: String,
    val description: String,
)

data class NewAlliance(
    val tag: String,
    val name: String,
    val playersCount: Long,
    val totalField: Long,
    val totalBuildingScore: Long,
    val totalTechnologyScore: Long,
    val totalTrophyScore: Long,
    val wars: List<String>,
    val pacts: List<NewPact>,
)

data class NewPact(
    val tag: String,
    val name: String,
    val description: String,
)
