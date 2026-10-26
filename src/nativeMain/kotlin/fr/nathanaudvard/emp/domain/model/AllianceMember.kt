package fr.nathanaudvard.emp.domain.model

data class AllianceMember(
    val id: Long,
    val pseudo: String,
    val masterPlayerId: Long?,
    // "C", "P1", "G3"... Null when the grade does not give one
    val chainRank: String?,
)

// The alliance writes the rank at the start of the grade: "G1 - Le roc", "P4 - Hypnos", "C - Styx", or alone: "P1"
private val chainRankPattern = Regex("""^\s*([CGP]\d*)\s*(?:-|$)""")

fun chainRankOf(grade: String?): String? =
    grade?.let { chainRankPattern.find(it)?.groupValues?.get(1) }
