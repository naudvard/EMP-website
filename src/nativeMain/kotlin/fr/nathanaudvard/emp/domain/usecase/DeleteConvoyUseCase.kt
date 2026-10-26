package fr.nathanaudvard.emp.domain.usecase

import fr.nathanaudvard.emp.service.WageService

// A convoy cancelled in the game, or recorded by mistake
class DeleteConvoyUseCase(private val wages: WageService) {
    suspend operator fun invoke(id: Long, fromPlayerId: Long) = wages.deleteConvoy(id, fromPlayerId)
}
