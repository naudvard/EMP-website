package fr.nathanaudvard.emp.domain.model

data class Alliance(
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
    val tag: String,
    val name: String,
    val description: String,
)
