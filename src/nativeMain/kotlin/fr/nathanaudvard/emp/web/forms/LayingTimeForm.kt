package fr.nathanaudvard.emp.web.forms

import fr.nathanaudvard.emp.domain.model.AllianceMember
import fr.nathanaudvard.emp.domain.model.LayingTimeFactor
import io.ktor.http.Parameters

data class LayingTimeForm(
    val pseudo: String = "",
    val factor: String = "",
    val level: String = "",
) {
    fun validate(members: List<AllianceMember>): LayingTimeValidation {
        val member = members.find { it.pseudo == pseudo }
        val selectedFactor = LayingTimeFactor.entries.find { it.name == factor }
        val parsedLevel = level.toIntOrNull()?.takeIf { it >= 0 }

        if (member != null && selectedFactor != null && parsedLevel != null) {
            return LayingTimeValidation.Valid(member, selectedFactor, parsedLevel.toLong())
        }
        return LayingTimeValidation.Invalid(
            buildMap {
                if (member == null) put(PSEUDO, "Choisis un pseudo de l'alliance dans la liste.")
                if (selectedFactor == null) put(FACTOR, "Choisis un élément.")
                if (parsedLevel == null) put(LEVEL, "Le niveau doit être un entier positif ou nul.")
            },
        )
    }

    companion object {
        const val PSEUDO = "pseudo"
        const val FACTOR = "factor"
        const val LEVEL = "level"

        fun from(parameters: Parameters) = LayingTimeForm(
            pseudo = parameters[PSEUDO].orEmpty().trim(),
            factor = parameters[FACTOR].orEmpty(),
            level = parameters[LEVEL].orEmpty().trim(),
        )
    }
}

sealed interface LayingTimeValidation {
    data class Valid(val member: AllianceMember, val factor: LayingTimeFactor, val level: Long) : LayingTimeValidation

    data class Invalid(val errors: Map<String, String>) : LayingTimeValidation
}
