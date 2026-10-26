package fr.nathanaudvard.emp.domain.usecase

import fr.nathanaudvard.emp.domain.model.WageDelivery
import fr.nathanaudvard.emp.service.WageService
import kotlin.time.Clock

// Convoys are added to those recorded before: none is ever replaced
class RecordConvoysUseCase(private val wages: WageService, private val clock: Clock) {
    suspend operator fun invoke(computationId: Long, convoys: List<WageDelivery>) =
        wages.saveConvoys(computationId, clock.now(), convoys)
}
