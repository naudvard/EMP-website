package fr.nathanaudvard.emp

import fr.nathanaudvard.emp.api.dto.ErrorResponse
import fr.nathanaudvard.emp.api.dto.WageComputationResponse
import fr.nathanaudvard.emp.api.dto.WageResponse
import fr.nathanaudvard.emp.api.resources.Alliances
import fr.nathanaudvard.emp.api.resources.Exports
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

class ComputeWagesTest {
    private val abeille = fourmi.copy(id = 124, pseudo = "Abeille")
    private val intrus = fourmi.copy(id = 125, pseudo = "Intrus", alliance = "LRM")

    private suspend fun AppTestScope.wages(tag: String = "EMP", distribution: String = "minimal"): HttpResponse =
        post(Alliances.Tag.Wages(Alliances.Tag(tag = tag), distribution))

    private suspend fun AppTestScope.saveLevel(pseudo: String, kind: String, level: Int) {
        signIn(pseudo)
        client.submitForm("/me/levels/$kind", parameters { append("level", level.toString()) })
    }

    private fun WageComputationResponse.wage(pseudo: String): WageResponse = wages.single { it.pseudo == pseudo }

    private fun assertClose(expected: Double, actual: Double) =
        assertTrue(abs(expected - actual) <= 1e-6 * abs(expected), "expected $expected, got $actual")

    @Test
    fun `averages one field measure per hour over the last 24 hours and moves colony production to the master`() = appTest {
        fourmizzz.players("202610190900", listOf(fourmi.copy(field = 999_999)))
        fourmizzz.players("202610201100", listOf(fourmi.copy(field = 999_999), abeille, intrus))
        fourmizzz.players("202610201130", listOf(fourmi.copy(field = 1_000), abeille.copy(field = 500, masterPlayerId = 123), intrus))
        fourmizzz.players("202610201200", listOf(fourmi.copy(field = 3_000), abeille.copy(field = 500, masterPlayerId = 123), intrus))
        sync(Exports.Players.Sync(version = "202610201200"))
        saveLevel("Fourmi", "APHID_FARM", 5)

        val res = wages()

        assertEquals(HttpStatusCode.OK, res.status)
        val computation = res.body<WageComputationResponse>()
        assertEquals("2026-10-19T12:30:00Z", computation.since)
        assertEquals("2026-10-20T12:30:00Z", computation.computedAt)
        assertEquals("202610201200", computation.exportVersion)
        assertEquals(2, computation.hours)
        assertEquals(listOf("Abeille", "Fourmi"), computation.wages.map { it.pseudo }.sorted())
        assertClose(2_000.0, computation.wage("Fourmi").averageField)
        assertEquals(96_000L + 6_000, computation.wage("Fourmi").selfProduction)
        assertEquals(24_000L - 6_000, computation.wage("Abeille").selfProduction)
        assertEquals(60_000L, computation.wage("Fourmi").grossWage)
        assertEquals(-42_000L, computation.wage("Fourmi").netWage)
        assertEquals(42_000L, computation.wage("Abeille").netWage)
        assertEquals(listOf(Triple("Fourmi", "Abeille", 42_000L)), computation.deliveries.map { Triple(it.fromPseudo, it.toPseudo, it.amount) })
        with(computation.wage("Abeille")) {
            assertEquals(listOf(24_000L, -6_000L, 123L, 42_000L), listOf(baseProduction, colonyTransfer, masterPlayerId, settledAmount))
        }
        with(computation.wage("Fourmi")) {
            assertEquals(listOf(96_000L, 6_000L, null, -42_000L), listOf(baseProduction, colonyTransfer, masterPlayerId, settledAmount))
        }

        val syncedVersions = query("SELECT DISTINCT version FROM exported_players ORDER BY version") { it.getString(0) }
        assertEquals(listOf("202610201100", "202610201130", "202610201200"), syncedVersions)
        val stored = query("SELECT alliance_tag, since, computed_at, export_version, hours, distribution FROM wage_computations") { cursor ->
            (0..5).map { cursor.getString(it) }
        }
        assertEquals(listOf(listOf("EMP", "2026-10-19T12:30:00Z", "2026-10-20T12:30:00Z", "202610201200", "2", "minimal")), stored)
        assertEquals(listOf(2L), query("SELECT count(*) FROM wages") { it.getLong(0) })
        val deliveries = query("SELECT from_player_id, to_player_id, amount FROM wage_deliveries") { cursor ->
            (0..2).map { cursor.getLong(it) }
        }
        assertEquals(listOf(listOf(123L, 124L, 42_000L)), deliveries)
    }

    @Test
    fun `shares the alliance production according to laying time needs`() = appTest {
        fourmizzz.players("202610201200", listOf(fourmi, abeille))
        sync(Exports.Players.Sync())
        saveLevel("Fourmi", "INCUBATOR", 2)
        saveLevel("Fourmi", "SOLARIUM", 1)

        val computation = wages().body<WageComputationResponse>()

        val total = 2 * fourmi.field * 48
        assertEquals(3, computation.wage("Fourmi").layingTime)
        assertEquals(20_000L, computation.wage("Fourmi").theoreticalNeed)
        assertEquals(total * 2 / 3, computation.wage("Fourmi").grossWage)
        assertEquals(total / 3, computation.wage("Abeille").grossWage)
    }

    @Test
    fun `distributes granary surpluses with as few deliveries as possible or proportionally`() = appTest {
        val fields = mapOf(1L to 4_000L, 2L to 3_000L, 3L to 500L, 4L to 1_000L)
        val players = fields.map { (id, field) -> fourmi.copy(id = id, pseudo = "P$id", field = field) }
        // A day apart, so that both share a day of production
        clock.now = NOW - 24.hours
        fourmizzz.players("202610191200", players)
        val minimal = wages().body<WageComputationResponse>()
        clock.now = NOW
        fourmizzz.players("202610201200", players)
        val proportional = wages(distribution = "proportional").body<WageComputationResponse>()

        fun WageComputationResponse.received() = deliveries.groupBy { it.toPseudo }.mapValues { (_, d) -> d.sumOf { it.amount } }
        fun WageComputationResponse.given() = deliveries.groupBy { it.fromPseudo }.mapValues { (_, d) -> d.sumOf { it.amount } }
        assertEquals(3, minimal.deliveries.size)
        assertEquals(mapOf("P3" to 78_000L, "P4" to 54_000L), minimal.received())
        assertEquals(mapOf("P1" to 90_000L, "P2" to 42_000L), minimal.given())
        // Each proportional share is rounded down
        assertEquals(4, proportional.deliveries.size)
        assertEquals(mapOf("P3" to 77_999L, "P4" to 53_999L), proportional.received())
        assertEquals(mapOf("P1" to 89_999L, "P2" to 41_999L), proportional.given())
        assertEquals(listOf(2L), query("SELECT count(*) FROM wage_computations") { it.getLong(0) })
    }

    @Test
    fun `a giver accumulating debt delivers nothing then repays it by not receiving and by delivering`() = appTest {
        // Fourmi gives 42_000 to Abeille each day
        val players = listOf(fourmi.copy(field = 2_000), abeille.copy(field = 500, masterPlayerId = 123))
        clock.now = NOW - 24.hours
        fourmizzz.players("202610191200", players)
        sync(Exports.Players.Sync())
        saveLevel("Fourmi", "APHID_FARM", 5)
        execute("UPDATE players SET wage_settlement = 'ACCUMULATE_DEBT' WHERE id = 123")

        val accumulated = wages().body<WageComputationResponse>()

        assertEquals(-42_000L, accumulated.wage("Fourmi").netWage)
        assertEquals(emptyList(), accumulated.deliveries)
        assertEquals(listOf(42_000L), query("SELECT debt FROM players WHERE id = 123") { it.getLong(0) })

        execute("UPDATE players SET wage_settlement = 'DELIVER' WHERE id = 123")
        clock.now = NOW
        fourmizzz.players("202610201200", players)
        val repaid = wages().body<WageComputationResponse>()

        assertEquals(listOf(Triple("Fourmi", "Abeille", 84_000L)), repaid.deliveries.map { Triple(it.fromPseudo, it.toPseudo, it.amount) })
        assertEquals(listOf(0L, 0L), query("SELECT debt FROM players ORDER BY id") { it.getLong(0) })
    }

    @Test
    fun `keeps the detail of each wage and tells when an older computation has none`() = appTest {
        fourmizzz.players("202610201200", listOf(fourmi.copy(field = 2_000), abeille.copy(field = 500, masterPlayerId = 123)))
        sync(Exports.Players.Sync())
        saveLevel("Fourmi", "APHID_FARM", 5)
        wages()

        val computation = latestComputation()
        assertTrue(computation.hasDetail)
        assertEquals(listOf("Abeille"), computation.colonies(123).map { it.pseudo })
        assertClose(0.5, computation.needShare(123))
        assertEquals(42_000L, computation.wage("Abeille")?.settledAmount)

        execute("UPDATE wages SET base_production = NULL, colony_transfer = NULL, master_player_id = NULL, settled_amount = NULL")
        assertFalse(latestComputation().hasDetail)
    }

    @Test
    fun `shares the production since the previous computation with the measures taken since`() = appTest {
        clock.now = NOW - 6.hours
        fourmizzz.players("202610200600", listOf(fourmi.copy(field = 999_999), abeille))
        wages()
        clock.now = NOW
        fourmizzz.players("202610200700", listOf(fourmi.copy(field = 2_000), abeille))
        fourmizzz.players("202610201200", listOf(fourmi.copy(field = 4_000), abeille))

        val computation = wages().body<WageComputationResponse>()

        assertEquals("2026-10-20T06:30:00Z", computation.since)
        assertEquals(2, computation.hours)
        assertClose(3_000.0, computation.wage("Fourmi").averageField)
        // A quarter of a day
        assertEquals(36_000L, computation.wage("Fourmi").selfProduction)
    }

    @Test
    fun `returns 404 without any players export since the previous computation`() = appTest {
        fourmizzz.players("202610201200", listOf(fourmi))
        wages()
        clock.now = NOW + 1.hours

        assertEquals(HttpStatusCode.NotFound, wages().status)
        assertEquals(listOf(1L), query("SELECT count(*) FROM wage_computations") { it.getLong(0) })
    }

    @Test
    fun `refuses levels saved before they were bounded instead of computing wrong wages`() = appTest {
        fourmizzz.players("202610201200", listOf(fourmi, abeille))
        sync(Exports.Players.Sync())
        execute("UPDATE players SET incubator_level = 1000 WHERE id = 123")

        val res = wages()

        assertEquals(HttpStatusCode.UnprocessableEntity, res.status)
        assertEquals("Levels of player 123 are too high to compute a laying need", res.body<ErrorResponse>().message)
        assertEquals(listOf(0L), query("SELECT count(*) FROM wage_computations") { it.getLong(0) })
    }

    @Test
    fun `syncs the alliance from a version stored for another alliance only`() = appTest {
        fourmizzz.players("202610201200", listOf(fourmi, abeille, intrus))
        sync(Exports.Players.Sync(), tags = listOf("LRM"))

        val computation = wages().body<WageComputationResponse>()

        assertEquals("202610201200", computation.exportVersion)
        assertEquals(listOf("Abeille", "Fourmi"), computation.wages.map { it.pseudo }.sorted())
        val tags = query("SELECT DISTINCT alliance_tag FROM exported_players ORDER BY alliance_tag") { it.getString(0) }
        assertEquals(listOf("EMP", "LRM"), tags)
    }

    @Test
    fun `rejects an unknown distribution`() = appTest {
        fourmizzz.players("202610201200", listOf(fourmi))

        assertEquals(HttpStatusCode.BadRequest, wages(distribution = "random").status)
    }

    @Test
    fun `returns 404 without any players export in the last 24 hours`() = appTest {
        fourmizzz.players("202610190900", listOf(fourmi))

        assertEquals(HttpStatusCode.NotFound, wages().status)
    }

    @Test
    fun `returns 404 for an alliance without members`() = appTest {
        fourmizzz.players("202610201200", listOf(fourmi))

        assertEquals(HttpStatusCode.NotFound, wages(tag = "XYZ").status)
    }
}
