package fr.nathanaudvard.emp.web.forms

import fr.nathanaudvard.emp.domain.model.AllianceMember
import fr.nathanaudvard.emp.domain.model.PlayerLevel
import io.ktor.http.Parameters

data class LevelsForm(
    val pseudo: String = "",
    val kind: String = "",
    val level: String = "",
) {
    fun validate(members: List<AllianceMember>): LevelsValidation {
        val member = members.find { it.pseudo == pseudo }
        val selectedKind = PlayerLevel.entries.find { it.name == kind }
        val parsedLevel = level.toIntOrNull()?.takeIf { it in 0..PlayerLevel.MAX_LEVEL }

        if (member != null && selectedKind != null && parsedLevel != null) {
            return LevelsValidation.Valid(member, selectedKind, parsedLevel.toLong())
        }
        return LevelsValidation.Invalid(
            buildMap {
                if (member == null) put(PSEUDO, "Choisis un pseudo de l'alliance dans la liste.")
                if (selectedKind == null) put(KIND, "Choisis un élément.")
                if (parsedLevel == null) put(LEVEL, "Le niveau doit être un entier entre 0 et ${PlayerLevel.MAX_LEVEL}.")
            },
        )
    }

    companion object {
        const val PSEUDO = "pseudo"
        const val KIND = "kind"
        const val LEVEL = "level"

        fun from(parameters: Parameters) = LevelsForm(
            pseudo = parameters[PSEUDO].orEmpty().trim(),
            kind = parameters[KIND].orEmpty(),
            level = parameters[LEVEL].orEmpty().trim(),
        )
    }
}

sealed interface LevelsValidation {
    data class Valid(val member: AllianceMember, val kind: PlayerLevel, val level: Long) :
        LevelsValidation

    data class Invalid(val errors: Map<String, String>) : LevelsValidation
}
