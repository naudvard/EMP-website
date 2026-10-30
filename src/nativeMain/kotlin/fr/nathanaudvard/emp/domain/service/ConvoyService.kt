package fr.nathanaudvard.emp.domain.service

import fr.nathanaudvard.emp.database.WageRepository
import fr.nathanaudvard.emp.domain.helper.PastedConvoy
import fr.nathanaudvard.emp.domain.helper.WageHelper
import fr.nathanaudvard.emp.domain.helper.parseConvoyMessages
import fr.nathanaudvard.emp.domain.helper.sortPastedConvoys
import fr.nathanaudvard.emp.domain.model.ConvoyPaste
import fr.nathanaudvard.emp.domain.model.ConvoySource
import fr.nathanaudvard.emp.domain.model.DuplicateConvoy
import fr.nathanaudvard.emp.domain.model.RecordedConvoy
import fr.nathanaudvard.emp.domain.model.RefusedConvoy
import fr.nathanaudvard.emp.domain.model.WageDelivery
import kotlin.time.Clock

class ConvoyService(private val wages: WageHelper, private val repository: WageRepository, private val clock: Clock) {
    // "J'ai envoyé": a convoy of what is left of the delivery, without a message. Only towards the latest computation or
    // the one before, the only ones shown. Null when nothing was left to send
    suspend fun declare(allianceTag: String, computationId: Long, fromPlayerId: Long, toPlayerId: Long, declaredBy: Long): Long? {
        val (latest, previous) = wages.latestTwo(allianceTag)
        val computation = listOfNotNull(latest, previous).find { it.id == computationId }?.computation ?: return null
        val delivery = computation.deliveries.find { it.fromPlayerId == fromPlayerId && it.toPlayerId == toPlayerId }
            ?: return null
        val left = delivery.amount - computation.covered(delivery)
        if (left <= 0) return null
        return repository.saveConvoy(
            computationId,
            WageDelivery(fromPlayerId, toPlayerId, left),
            clock.now(),
            ConvoySource.DECLARED,
            arrivesAt = null,
            returnAt = null,
            declaredBy,
        )
    }

    // The game's messages, a line or the whole convoys page: what is valid is recorded, the rest said
    suspend fun paste(allianceTag: String, senderId: Long, text: String, declaredBy: Long): ConvoyPaste {
        val now = clock.now()
        val messages = parseConvoyMessages(text, now)
        val (latest, previous) = wages.latestTwo(allianceTag)
        if (messages.isEmpty() || latest == null) return ConvoyPaste()
        val sorted = sortPastedConvoys(messages, senderId, listOfNotNull(latest, previous))
        val recorded = sorted.filterIsInstance<PastedConvoy.New>().map {
            val id = repository.saveConvoy(
                it.computationId,
                it.delivery,
                now,
                ConvoySource.PASTED,
                it.message.arrivesAt,
                it.message.returnAt,
                declaredBy,
            )
            RecordedConvoy(id, it.recipient, it.delivery.amount, it.message.arrivesAt)
        }
        val duplicates = sorted.filterIsInstance<PastedConvoy.Duplicate>().map {
            if (it.completes) repository.setConvoyTimes(it.existing.id, it.message.arrivesAt, it.message.returnAt)
            DuplicateConvoy(it.recipient, it.existing.amount, it.existing.sentAt)
        }
        val refused = sorted.filterIsInstance<PastedConvoy.Refused>().map { RefusedConvoy(it.message.recipient, it.message.materials) }
        return ConvoyPaste(recorded, duplicates, refused)
    }

    // Convoys cancelled in the game, or recorded by mistake. Only those of the given sender are removed
    suspend fun delete(ids: Collection<Long>, fromPlayerId: Long) = repository.deleteConvoys(ids, fromPlayerId)
}
