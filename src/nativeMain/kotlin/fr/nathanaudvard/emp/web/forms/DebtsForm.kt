package fr.nathanaudvard.emp.web.forms

import fr.nathanaudvard.emp.domain.model.AllianceMember
import fr.nathanaudvard.emp.domain.model.WageSettlement
import io.ktor.http.Parameters

// One choice per player, keyed by their id
data class DebtsForm(val choices: Map<String, String>) {
    // A player who left the alliance since the page was loaded is skipped
    fun validate(members: List<AllianceMember>): DebtsValidation {
        val memberIds = members.map { it.id }.toSet()
        val settlements = choices.mapNotNull { (id, choice) ->
            val playerId = id.toLongOrNull()?.takeIf { it in memberIds } ?: return@mapNotNull null
            val settlement = WageSettlement.entries.find { it.name == choice }
                ?: return DebtsValidation.Invalid("Choisis « livrer » ou « cumuler la dette » pour chaque joueur.")
            playerId to settlement
        }
        return DebtsValidation.Valid(settlements.toMap())
    }

    companion object {
        private const val PREFIX = "player-"

        fun fieldName(member: AllianceMember) = "$PREFIX${member.id}"

        fun from(parameters: Parameters) = DebtsForm(
            parameters.names()
                .filter { it.startsWith(PREFIX) }
                .associate { it.removePrefix(PREFIX) to parameters[it].orEmpty() },
        )
    }
}

sealed interface DebtsValidation {
    data class Valid(val settlements: Map<Long, WageSettlement>) : DebtsValidation

    data class Invalid(val error: String) : DebtsValidation
}
