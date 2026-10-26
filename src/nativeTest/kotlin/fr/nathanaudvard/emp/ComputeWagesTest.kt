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
import kotlin.test.assertTrue

class ComputeWagesTest {
    private val abeille = fourmi.copy(id = 124, pseudo = "Abeille")
    private val intrus = fourmi.copy(id = 125, pseudo = "Intrus", alliance = "LRM")

    private suspend fun AppTestScope.wages(tag: String = "EMP", distribution: String = "minimal"): HttpResponse =
        post(Alliances.Tag.Wages(Alliances.Tag(tag = tag), distribution))

    private suspend fun AppTestScope.saveLevel(pseudo: String, kind: String, level: Int) {
        client.submitForm(
            "/update-levels",
            parameters {
                append("pseudo", pseudo)
                append("kind", kind)
                append("level", level.toString())
            },
        )
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
        post(Exports.Players.Sync(version = "202610201200"))
        saveLevel("Fourmi", "APHID_FARM", 5)

        val res = wages()

        assertEquals(HttpStatusCode.OK, res.status)
        val computation = res.body<WageComputationResponse>()
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

        val syncedVersions = query("SELECT DISTINCT version FROM exported_players ORDER BY version") { it.getString(0) }
        assertEquals(listOf("202610201100", "202610201130", "202610201200"), syncedVersions)
        val stored = query("SELECT alliance_tag, computed_at, export_version, hours, distribution FROM wage_computations") { cursor ->
            (0..4).map { cursor.getString(it) }
        }
        assertEquals(listOf(listOf("EMP", "2026-10-20T12:30:00Z", "202610201200", "2", "minimal")), stored)
        assertEquals(listOf(2L), query("SELECT count(*) FROM wages") { it.getLong(0) })
        val deliveries = query("SELECT from_player_id, to_player_id, amount FROM wage_deliveries") { cursor ->
            (0..2).map { cursor.getLong(it) }
        }
        assertEquals(listOf(listOf(123L, 124L, 42_000L)), deliveries)
    }

    @Test
    fun `shares the alliance production according to laying time needs`() = appTest {
        fourmizzz.players("202610201200", listOf(fourmi, abeille))
        post(Exports.Players.Sync())
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
        fourmizzz.players("202610201200", fields.map { (id, field) -> fourmi.copy(id = id, pseudo = "P$id", field = field) })

        val minimal = wages().body<WageComputationResponse>()
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
    fun `refuses levels saved before they were bounded instead of computing wrong wages`() = appTest {
        fourmizzz.players("202610201200", listOf(fourmi, abeille))
        post(Exports.Players.Sync())
        execute("INSERT INTO players (id, incubator_level) VALUES (123, 1000)")

        val res = wages()

        assertEquals(HttpStatusCode.UnprocessableEntity, res.status)
        assertEquals("Levels of player 123 are too high to compute a laying need", res.body<ErrorResponse>().message)
        assertEquals(listOf(0L), query("SELECT count(*) FROM wage_computations") { it.getLong(0) })
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
