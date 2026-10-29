package fr.nathanaudvard.emp.domain.usecase

import fr.nathanaudvard.emp.domain.exception.AllianceNotFoundException
import fr.nathanaudvard.emp.domain.exception.ExportNotFoundException
import fr.nathanaudvard.emp.domain.exception.ExportUnavailableException
import fr.nathanaudvard.emp.domain.model.ComputationFailure
import fr.nathanaudvard.emp.domain.model.WageComputation
import fr.nathanaudvard.emp.service.AllianceSettingsService
import kotlin.coroutines.cancellation.CancellationException

sealed interface ComputationResult {
    data class Computed(val computation: WageComputation) : ComputationResult

    data class Failed(val failure: ComputationFailure, val cause: Throwable) : ComputationResult
}

// With the distribution saved for the alliance. The computation syncs the players exports it lacks itself, the
// latest one included
class ComputeWagesNowUseCase(
    private val computeWages: ComputeWagesUseCase,
    private val settings: AllianceSettingsService,
) {
    suspend operator fun invoke(allianceTag: String): ComputationResult = try {
        ComputationResult.Computed(computeWages(allianceTag, settings.settings(allianceTag).distribution))
    } catch (e: CancellationException) {
        throw e
    } catch (e: ExportNotFoundException) {
        ComputationResult.Failed(ComputationFailure.NO_EXPORT, e)
    } catch (e: AllianceNotFoundException) {
        ComputationResult.Failed(ComputationFailure.NO_MEMBERS, e)
    } catch (e: ExportUnavailableException) {
        ComputationResult.Failed(ComputationFailure.FOURMIZZZ_UNAVAILABLE, e)
    } catch (e: Exception) {
        ComputationResult.Failed(ComputationFailure.UNEXPECTED, e)
    }
}
