package fr.nathanaudvard.emp.domain.usecase

import fr.nathanaudvard.emp.domain.model.ConvoySource
import fr.nathanaudvard.emp.domain.model.WageDelivery
import fr.nathanaudvard.emp.service.WageService
import kotlin.time.Clock

// "J'ai envoyé": a convoy of what is left of the delivery, without a message. Only towards the latest computation or
// the one before, the only ones shown. Null when nothing was left to send
class DeclareConvoyUseCase(private val wages: WageService, private val clock: Clock) {
    suspend operator fun invoke(allianceTag: String, computationId: Long, fromPlayerId: Long, toPlayerId: Long, declaredBy: Long): Long? {
        val (latest, previous) = wages.latestTwo(allianceTag)
        val computation = listOfNotNull(latest, previous).find { it.id == computationId }?.computation ?: return null
        val delivery = computation.deliveries.find { it.fromPlayerId == fromPlayerId && it.toPlayerId == toPlayerId }
            ?: return null
        val left = delivery.amount - computation.covered(delivery)
        if (left <= 0) return null
        return wages.saveConvoy(
            computationId,
            WageDelivery(fromPlayerId, toPlayerId, left),
            clock.now(),
            ConvoySource.DECLARED,
            declaredBy,
        )
    }
}
