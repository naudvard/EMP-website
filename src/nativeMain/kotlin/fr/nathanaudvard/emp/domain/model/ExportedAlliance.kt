package fr.nathanaudvard.emp.domain.model

data class ExportedAlliance(
    val tag: String,
    val name: String,
    val playersCount: Long,
    val totalField: Long,
    val totalBuildingScore: Long,
    val totalTechnologyScore: Long,
    val totalTrophyScore: Long,
    val wars: List<String>,
    val pacts: List<ExportedPact>,
)

data class ExportedPact(
    val tag: String,
    val name: String,
    val description: String,
)
