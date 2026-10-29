package fr.nathanaudvard.emp.domain.usecase

import fr.nathanaudvard.emp.domain.model.ConvoyPaste
import fr.nathanaudvard.emp.domain.model.ConvoySource
import fr.nathanaudvard.emp.domain.model.DuplicateConvoy
import fr.nathanaudvard.emp.domain.model.RecordedConvoy
import fr.nathanaudvard.emp.domain.model.RefusedConvoy
import fr.nathanaudvard.emp.service.PastedConvoy
import fr.nathanaudvard.emp.service.WageService
import fr.nathanaudvard.emp.service.parseConvoyMessages
import fr.nathanaudvard.emp.service.sortPastedConvoys
import kotlin.time.Clock

// The game's messages, a line or the whole convoys page: what is valid is recorded, the rest said
class PasteConvoysUseCase(private val wages: WageService, private val clock: Clock) {
    suspend operator fun invoke(allianceTag: String, senderId: Long, text: String, declaredBy: Long): ConvoyPaste {
        val now = clock.now()
        val messages = parseConvoyMessages(text, now)
        val (latest, previous) = wages.latestTwo(allianceTag)
        if (messages.isEmpty() || latest == null) return ConvoyPaste()
        val sorted = sortPastedConvoys(messages, senderId, listOfNotNull(latest, previous))
        val recorded = sorted.filterIsInstance<PastedConvoy.New>().map {
            val id = wages.saveConvoy(
                it.computationId,
                it.delivery,
                now,
                ConvoySource.PASTED,
                declaredBy,
                it.message.arrivesAt,
                it.message.returnAt,
            )
            RecordedConvoy(id, it.recipient, it.delivery.amount, it.message.arrivesAt)
        }
        val duplicates = sorted.filterIsInstance<PastedConvoy.Duplicate>().map {
            if (it.completes) wages.setConvoyTimes(it.existing.id, it.message.arrivesAt, it.message.returnAt)
            DuplicateConvoy(it.recipient, it.existing.amount, it.existing.sentAt)
        }
        val refused = sorted.filterIsInstance<PastedConvoy.Refused>().map { RefusedConvoy(it.message.recipient, it.message.materials) }
        return ConvoyPaste(recorded, duplicates, refused)
    }
}
