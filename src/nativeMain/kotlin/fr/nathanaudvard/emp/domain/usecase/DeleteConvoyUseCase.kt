package fr.nathanaudvard.emp.domain.usecase

import fr.nathanaudvard.emp.service.WageService

// Convoys cancelled in the game, or recorded by mistake. Only those of the given sender are removed
class DeleteConvoyUseCase(private val wages: WageService) {
    suspend operator fun invoke(ids: Collection<Long>, fromPlayerId: Long) = wages.deleteConvoys(ids, fromPlayerId)
}
