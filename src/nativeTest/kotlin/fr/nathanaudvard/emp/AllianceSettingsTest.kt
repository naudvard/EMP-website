package fr.nathanaudvard.emp

import fr.nathanaudvard.emp.domain.model.AllianceSettings
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class AllianceSettingsTest {
    @Test
    fun `comes at the time set in game time whatever its offset`() {
        val settings = AllianceSettings(computationTime = LocalTime(18, 0))

        assertEquals(Instant.parse("2026-10-20T16:00:00Z"), settings.nextComputation(NOW))
        assertEquals(Instant.parse("2026-10-21T16:00:00Z"), settings.nextComputation(Instant.parse("2026-10-20T16:00:00Z")))
        // Summer time ends in the night of the 25th
        assertEquals(Instant.parse("2026-10-25T17:00:00Z"), settings.nextComputation(Instant.parse("2026-10-24T17:00:00Z")))
    }
}
