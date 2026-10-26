package fr.nathanaudvard.emp.web.forms

import fr.nathanaudvard.emp.domain.model.Wage
import fr.nathanaudvard.emp.domain.model.WageComputation
import fr.nathanaudvard.emp.domain.model.WageDelivery
import fr.nathanaudvard.emp.service.parseConvoyMessages
import io.ktor.http.Parameters

// The game's message once the sender launched one or several convoys
data class ConvoyForm(val message: String = "") {
    // A convoy is only recorded towards a delivery the computation plans
    fun validate(computation: WageComputation, sender: Wage): ConvoyValidation {
        val messages = parseConvoyMessages(message)
        if (messages.isEmpty()) {
            return ConvoyValidation.Invalid("Aucun convoi de matériaux reconnu. Colle le message « Vous allez livrer … » du jeu.")
        }
        val convoys = messages.map { convoy ->
            val recipient = computation.wage(convoy.recipient)
                ?: return ConvoyValidation.Invalid("${convoy.recipient} n'est pas dans le dernier calcul.")
            if (computation.deliveries.none { it.fromPlayerId == sender.playerId && it.toPlayerId == recipient.playerId }) {
                return ConvoyValidation.Invalid("Aucune livraison n'est prévue de ${sender.pseudo} à ${recipient.pseudo}.")
            }
            WageDelivery(sender.playerId, recipient.playerId, convoy.materials)
        }
        return ConvoyValidation.Valid(convoys)
    }

    companion object {
        const val MESSAGE = "message"

        fun from(parameters: Parameters) = ConvoyForm(parameters[MESSAGE].orEmpty())
    }
}

sealed interface ConvoyValidation {
    data class Valid(val convoys: List<WageDelivery>) : ConvoyValidation

    data class Invalid(val error: String) : ConvoyValidation
}
