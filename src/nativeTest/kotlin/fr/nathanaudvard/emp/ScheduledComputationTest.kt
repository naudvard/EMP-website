package fr.nathanaudvard.emp

import fr.nathanaudvard.emp.domain.model.AllianceSettings
import fr.nathanaudvard.emp.domain.model.ComputationFailure
import fr.nathanaudvard.emp.domain.model.WageDistribution
import fr.nathanaudvard.emp.domain.usecase.ComputationResult
import fr.nathanaudvard.emp.domain.usecase.RunScheduledComputationUseCase
import fr.nathanaudvard.emp.domain.usecase.SaveAllianceSettingsUseCase
import fr.nathanaudvard.emp.web.pages.NBSP
import fr.nathanaudvard.emp.web.resources.Wages
import io.ktor.client.plugins.resources.get
import io.ktor.client.statement.bodyAsText
import io.ktor.server.plugins.di.dependencies
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class ScheduledComputationTest {
    private val players = listOf(fourmi.copy(id = 1, pseudo = "P1", field = 4_000), fourmi.copy(id = 2, pseudo = "P2", field = 500))

    private suspend fun AppTestScope.run(): ComputationResult? {
        start()
        val run: RunScheduledComputationUseCase = application.dependencies.resolve()
        return run("EMP")
    }

    @Test
    fun `comes at the time set in game time whatever its offset`() {
        val settings = AllianceSettings(computationTime = LocalTime(18, 0))

        assertEquals(Instant.parse("2026-10-20T16:00:00Z"), settings.nextComputation(NOW))
        assertEquals(Instant.parse("2026-10-21T16:00:00Z"), settings.nextComputation(Instant.parse("2026-10-20T16:00:00Z")))
        // Summer time ends in the night of the 25th
        assertEquals(Instant.parse("2026-10-25T17:00:00Z"), settings.nextComputation(Instant.parse("2026-10-24T17:00:00Z")))
    }

    @Test
    fun `computes with the distribution saved and once within the minute`() = appTest {
        fourmizzz.players("202610201200", players)
        start()
        val save: SaveAllianceSettingsUseCase = application.dependencies.resolve()
        save("EMP", LocalTime(14, 30), WageDistribution.PROPORTIONAL, by = 1)

        assertIs<ComputationResult.Computed>(run())
        clock.now = NOW + 30.seconds

        assertNull(run())
        assertEquals(1L, wageHistory().bounds.latestId)
        assertEquals(WageDistribution.PROPORTIONAL, latestComputation().distribution)
    }

    @Test
    fun `tells the chiefs of a failure until a computation succeeds`() = appTest {
        fourmizzz.players("202610201200", players)
        assertIs<ComputationResult.Computed>(run())
        signIn("P1")
        clock.now = NOW + 2.hours

        val failed = run()

        assertEquals(ComputationFailure.NO_EXPORT, assertIs<ComputationResult.Failed>(failed).failure)
        assertEquals(NOW + 2.hours, settings().failedAt)
        assertTrue(
            "Le calcul automatique d'aujourd'hui 16:30 a échoué : aucun export joueurs Fourmizzz depuis le calcul précédent." in
                client.get(Wages()).bodyAsText().replace(NBSP, " ")
        )

        fourmizzz.players("202610201500", players)
        clock.now = NOW + 3.hours
        assertIs<ComputationResult.Computed>(run())
        assertNull(settings().failure)
        assertFalse("a échoué" in client.get(Wages()).bodyAsText())
    }
}
