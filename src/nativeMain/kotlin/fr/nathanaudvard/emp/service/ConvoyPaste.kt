package fr.nathanaudvard.emp.service

import fr.nathanaudvard.emp.domain.model.Convoy
import fr.nathanaudvard.emp.domain.model.ConvoySource
import fr.nathanaudvard.emp.domain.model.SavedComputation
import fr.nathanaudvard.emp.domain.model.WageDelivery

sealed interface PastedConvoy {
    val message: ConvoyMessage

    data class New(override val message: ConvoyMessage, val computationId: Long, val delivery: WageDelivery, val recipient: String) :
        PastedConvoy

    // A declared convoy found in a message gets its times
    data class Duplicate(override val message: ConvoyMessage, val existing: Convoy, val recipient: String) : PastedConvoy {
        val completes: Boolean get() = existing.returnAt == null && (message.arrivesAt != null || message.returnAt != null)
    }

    data class Refused(override val message: ConvoyMessage) : PastedConvoy
}

// The computations come latest first: a convoy goes to the first one planning a delivery to its recipient, the older
// ones only while something is left to send. A convoy recorded before with the same recipient, amount and return is
// the same one, as is a convoy declared without its times
internal fun sortPastedConvoys(
    messages: List<ConvoyMessage>,
    senderId: Long,
    computations: List<SavedComputation>,
): List<PastedConvoy> {
    val stored = computations.flatMap { saved -> saved.computation.convoys.filter { it.fromPlayerId == senderId } }
        .toMutableList()
    return messages.map { message ->
        val recipient = computations.firstNotNullOfOrNull { it.computation.wage(message.recipient) }
            ?: return@map PastedConvoy.Refused(message)
        val duplicate = stored.firstOrNull { convoy ->
            convoy.toPlayerId == recipient.playerId && convoy.amount == message.materials &&
                ((message.returnAt != null && convoy.returnAt == message.returnAt) ||
                    (convoy.source == ConvoySource.DECLARED && convoy.returnAt == null))
        }
        if (duplicate != null) {
            stored.remove(duplicate)
            return@map PastedConvoy.Duplicate(message, duplicate, recipient.pseudo)
        }
        val target = computations.withIndex().firstOrNull { (index, saved) ->
            val delivery = saved.computation.deliveries
                .find { it.fromPlayerId == senderId && it.toPlayerId == recipient.playerId }
            delivery != null && (index == 0 || saved.computation.covered(delivery) < delivery.amount)
        }?.value ?: return@map PastedConvoy.Refused(message)
        PastedConvoy.New(message, target.id, WageDelivery(senderId, recipient.playerId, message.materials), recipient.pseudo)
    }
}
