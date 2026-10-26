package fr.nathanaudvard.emp.domain.usecase

import fr.nathanaudvard.emp.domain.model.WageHistory
import fr.nathanaudvard.emp.service.WageService
import kotlin.time.Instant

class GetWageHistoryUseCase(private val wages: WageService) {
    // Selects the given computation, else the latest one computed before the given instant, else the latest one
    suspend operator fun invoke(allianceTag: String, computationId: Long? = null, before: Instant? = null): WageHistory {
        val bounds = wages.bounds(allianceTag)
        val selectedId = when {
            computationId != null -> computationId
            before != null -> wages.latestIdBefore(allianceTag, before)
            else -> bounds.latestId
        }
        val selected = selectedId?.let { wages.computation(allianceTag, it) }
        return WageHistory(
            bounds = bounds,
            selectedId = selectedId,
            selected = selected,
            olderId = selected?.let { wages.olderId(allianceTag, selectedId) },
            newerId = selected?.let { wages.newerId(allianceTag, selectedId) },
        )
    }
}
