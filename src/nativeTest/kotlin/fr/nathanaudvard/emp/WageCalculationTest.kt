package fr.nathanaudvard.emp

import fr.nathanaudvard.emp.domain.model.ExportVersion
import fr.nathanaudvard.emp.domain.model.Wage
import fr.nathanaudvard.emp.domain.model.WageDelivery
import fr.nathanaudvard.emp.domain.model.WageDistribution
import fr.nathanaudvard.emp.domain.usecase.architectureReduction
import fr.nathanaudvard.emp.domain.usecase.colonyShare
import fr.nathanaudvard.emp.domain.usecase.distribute
import fr.nathanaudvard.emp.domain.usecase.layingTechniqueResearchSeconds
import fr.nathanaudvard.emp.domain.usecase.theoreticalNeed
import kotlin.math.abs
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WageCalculationTest {
    private fun assertClose(expected: Double, actual: Double) =
        assertTrue(abs(expected - actual) <= 1e-9 * abs(expected), "expected $expected, got $actual")

    @Test
    fun `theoretical need doubles every three laying time levels`() {
        assertClose(10_000.0, theoreticalNeed(0))
        assertClose(20_000.0, theoreticalNeed(3))
        assertClose(10_000 * 2.0.pow(7 / 3.0), theoreticalNeed(7))
    }

    @Test
    fun `laying technique research time grows by 1_7 up to level 20 then by 1_3`() {
        assertClose(120.0, layingTechniqueResearchSeconds(1))
        assertClose(204.0, layingTechniqueResearchSeconds(2))
        assertClose(120 * 1.7.pow(19), layingTechniqueResearchSeconds(20))
        assertClose(120 * 1.7.pow(19) * 1.3 * 1.3, layingTechniqueResearchSeconds(22))
    }

    @Test
    fun `architecture and colony shares`() {
        assertClose(0.81, architectureReduction(2))
        assertClose(0.20, colonyShare(0))
        assertClose(0.25, colonyShare(5))
    }

    @Test
    fun `the remainder left by rounding down stays with the granaries`() {
        val template = Wage(0, "", 0.0, 0, 0, 0, 0.0, 0.0, 0, 0, 0)
        val wages = listOf(
            template.copy(playerId = 1, netWage = -10),
            template.copy(playerId = 2, netWage = 5),
            template.copy(playerId = 3, netWage = 4),
        )

        assertEquals(listOf(WageDelivery(1, 2, 5), WageDelivery(1, 3, 4)), distribute(wages, WageDistribution.MINIMAL))
        assertEquals(listOf(WageDelivery(1, 2, 5), WageDelivery(1, 3, 4)), distribute(wages, WageDistribution.PROPORTIONAL))
    }

    @Test
    fun `export versions are UTC yyyyMMddHHmm`() {
        assertEquals("202610191230", ExportVersion.of(NOW - kotlin.time.Duration.parse("24h")))
    }
}
