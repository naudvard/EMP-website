package fr.nathanaudvard.emp

import fr.nathanaudvard.emp.api.resources.Exports
import fr.nathanaudvard.emp.domain.model.AllianceMember
import fr.nathanaudvard.emp.domain.model.chainRankOf
import kotlin.test.Test
import kotlin.test.assertEquals

class ChainRankTest {
    @Test
    fun `reads the rank at the start of the grade`() {
        assertEquals("G1", chainRankOf("G1 - Le roc"))
        assertEquals("P4", chainRankOf("P4 - Portier des enfers"))
        assertEquals("C", chainRankOf("C - Hadès"))
        assertEquals("G", chainRankOf("G - Titan"))
        assertEquals("P12", chainRankOf("  P12 -Tonnerre"))
        assertEquals("P1", chainRankOf("P1"))
    }

    @Test
    fun `has no rank when the grade does not start with one`() {
        assertEquals(null, chainRankOf(null))
        assertEquals(null, chainRankOf("Chef"))
        assertEquals(null, chainRankOf("chef G0"))
        assertEquals(null, chainRankOf("Chasseur - P1"))
        assertEquals(null, chainRankOf("P2 de la ODF"))
        assertEquals(null, chainRankOf("Coursier 9 - G1"))
    }

    @Test
    fun `alliance members get their rank from the latest export`() = appTest {
        fourmizzz.players(
            "202610201200",
            listOf(
                fourmi.copy(id = 1, pseudo = "Stonotwo", grade = "G1 - Le roc"),
                fourmi.copy(id = 2, pseudo = "Verso", grade = "C - Hadès"),
                fourmi.copy(id = 3, pseudo = "Recrue", grade = null),
            ),
        )
        sync(Exports.Players.Sync(version = "202610201200"))

        assertEquals(
            listOf(
                AllianceMember(3, "Recrue", null, null, fourmi.field),
                AllianceMember(1, "Stonotwo", null, "G1", fourmi.field),
                AllianceMember(2, "Verso", null, "C", fourmi.field),
            ),
            members(),
        )
    }
}
