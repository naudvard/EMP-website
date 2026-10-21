package fr.nathanaudvard.emp.domain.model

data class AllianceMember(
    val id: Long,
    val pseudo: String,
    val masterPlayerId: Long?,
)
