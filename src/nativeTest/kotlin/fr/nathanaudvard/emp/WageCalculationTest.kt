package fr.nathanaudvard.emp

import fr.nathanaudvard.emp.domain.exception.LevelsTooHighException
import fr.nathanaudvard.emp.domain.model.AllianceMember
import fr.nathanaudvard.emp.domain.model.ExportVersion
import fr.nathanaudvard.emp.domain.model.PlayerLevel
import fr.nathanaudvard.emp.domain.model.PlayerLevels
import fr.nathanaudvard.emp.domain.model.SelfProduction
import fr.nathanaudvard.emp.domain.model.SettledWage
import fr.nathanaudvard.emp.domain.model.Wage
import fr.nathanaudvard.emp.domain.model.WageDelivery
import fr.nathanaudvard.emp.domain.model.WageDistribution
import fr.nathanaudvard.emp.domain.model.WageSettlement
import fr.nathanaudvard.emp.service.architectureReduction
import fr.nathanaudvard.emp.service.colonyShare
import fr.nathanaudvard.emp.service.distribute
import fr.nathanaudvard.emp.service.layingNeed
import fr.nathanaudvard.emp.service.layingTechniqueResearchSeconds
import fr.nathanaudvard.emp.service.selfProductions
import fr.nathanaudvard.emp.service.settleDebt
import fr.nathanaudvard.emp.service.theoreticalNeed
import kotlin.math.abs
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
    fun `a colony share is rounded once so that self productions add up to the harvest`() {
        val members = listOf(
            AllianceMember(1, "Maître", null, null),
            AllianceMember(2, "Colonie", 1, null),
            AllianceMember(3, "Colonie bis", 1, null),
            AllianceMember(4, "Seule", null, null),
            AllianceMember(5, "Colonie d'ailleurs", 99, null),
        )
        val fields = mapOf(1L to 1_001.3, 2L to 333.7, 3L to 57.9, 4L to 12.1, 5L to 640.3)

        val productions = selfProductions(members, fields, mapOf(1L to 7L), 0.37)

        assertEquals(productions.values.sumOf { it.baseProduction }, productions.values.sumOf { it.selfProduction })
        val given = listOf(2L, 3L).map { productions.getValue(it) }
        assertEquals(listOf(-1_600L, -277L), given.map { it.colonyTransfer })
        assertEquals(1_877L, productions.getValue(1).colonyTransfer)
        assertEquals(listOf(1L, 1L), given.map { it.masterPlayerId })
        assertEquals(SelfProduction(11_371, 0, null), productions.getValue(5))
    }

    @Test
    fun `the remainder left by rounding down stays with the granaries`() {
        val wages = listOf(settled(1, -10), settled(2, 5), settled(3, 4))

        assertEquals(listOf(WageDelivery(1, 2, 5), WageDelivery(1, 3, 4)), distribute(wages, WageDistribution.MINIMAL))
        assertEquals(listOf(WageDelivery(1, 2, 5), WageDelivery(1, 3, 4)), distribute(wages, WageDistribution.PROPORTIONAL))
    }

    @Test
    fun `a receiver repays their debt by not receiving their wage whatever their choice`() {
        assertEquals(SettledWage(1, 30, 0, 20), settleDebt(wage(1, 30), WageSettlement.DELIVER, 50))
        assertEquals(SettledWage(1, 30, 10, 0), settleDebt(wage(1, 30), WageSettlement.ACCUMULATE_DEBT, 20))
    }

    @Test
    fun `a giver who delivers repays their whole debt and one who accumulates owes their wage on top of it`() {
        assertEquals(SettledWage(1, -30, -80, 0), settleDebt(wage(1, -30), WageSettlement.DELIVER, 50))
        assertEquals(SettledWage(1, -30, 0, 80), settleDebt(wage(1, -30), WageSettlement.ACCUMULATE_DEBT, 50))
    }

    @Test
    fun `receivers share what the debts repay or hold back`() {
        // Player 2 repays 20 by delivering, player 3 repays 10 by not receiving: player 4 gets both
        val repaid = listOf(settled(1, -60), settled(2, -10, debt = 20), settled(3, 30, debt = 10), settled(4, 40))
        assertEquals(
            listOf(WageDelivery(1, 4, 60), WageDelivery(2, 4, 10), WageDelivery(2, 3, 20)),
            distribute(repaid, WageDistribution.MINIMAL),
        )
        // Player 2 keeps the 50 they owe: receivers get 50 out of 100, half of their wage each
        val heldBack = listOf(settled(1, -50), settled(2, -50, accumulate = true), settled(3, 60), settled(4, 40))
        assertEquals(listOf(WageDelivery(1, 3, 30), WageDelivery(1, 4, 20)), distribute(heldBack, WageDistribution.MINIMAL))
        assertEquals(listOf(WageDelivery(1, 3, 30), WageDelivery(1, 4, 20)), distribute(heldBack, WageDistribution.PROPORTIONAL))
    }

    private fun wage(playerId: Long, netWage: Long) = Wage(playerId, "", 0.0, 0, 0, 0, 0.0, 0.0, 0, 0, netWage)

    private fun settled(playerId: Long, netWage: Long, debt: Long = 0, accumulate: Boolean = false) =
        settleDebt(
            wage(playerId, netWage),
            if (accumulate) WageSettlement.ACCUMULATE_DEBT else WageSettlement.DELIVER,
            debt,
        )

    @Test
    fun `laying needs stay within Long up to the highest accepted levels`() {
        val max = PlayerLevel.MAX_LEVEL.toLong()
        for (layingTechnique in 0..max) {
            layingNeed(1, PlayerLevels(incubator = max, solarium = max, layingTechnique = layingTechnique))
        }
        assertFailsWith<LevelsTooHighException> { layingNeed(1, PlayerLevels(incubator = 1_000)) }
    }

    @Test
    fun `export versions are UTC yyyyMMddHHmm`() {
        assertEquals("202610191230", ExportVersion.of(NOW - kotlin.time.Duration.parse("24h")))
    }
}
