package fr.nathanaudvard.emp

import fr.nathanaudvard.emp.api.dto.ErrorResponse
import fr.nathanaudvard.emp.api.dto.SyncResponse
import fr.nathanaudvard.emp.api.resources.Exports
import fr.nathanaudvard.emp.domain.model.ExportType
import io.ktor.client.call.body
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class SyncExportedPlayersTest {
    @Test
    fun `stores the requested version keeping nullable fields and booleans`() = appTest {
        val unaligned = fourmi.copy(id = 124, alliance = null, grade = null, masterPlayerId = 123, onHoliday = true)
        fourmizzz.players("202610201200", listOf(fourmi, unaligned))

        val res = post(Exports.Players.Sync(version = "202610201200"))

        assertEquals(HttpStatusCode.OK, res.status)
        assertEquals(SyncResponse("202610201200", 2), res.body<SyncResponse>())
        val rows = query(
            "SELECT version, id, alliance_tag, master_player_id, grade, on_holiday, is_banned FROM exported_players ORDER BY id",
        ) { (0..6).map { i -> it.getString(i) } }
        assertEquals(
            listOf(
                listOf("202610201200", "123", "EMP", null, "Chef", "0", "0"),
                listOf("202610201200", "124", null, "123", null, "1", "0"),
            ),
            rows,
        )
    }

    @Test
    fun `stores the latest export under its real Fourmizzz version`() = appTest {
        fourmizzz.players("202610201100", listOf(fourmi.copy(pseudo = "old")))
        fourmizzz.players("202610201200", listOf(fourmi.copy(pseudo = "new")))

        val res = post(Exports.Players.Sync())

        assertEquals(SyncResponse("202610201200", 1), res.body<SyncResponse>())
        val rows = query("SELECT version, pseudo FROM exported_players") { it.getString(0) to it.getString(1) }
        assertEquals(listOf("202610201200" to "new"), rows)
    }

    @Test
    fun `keeps each version separately`() = appTest {
        fourmizzz.players("202610201100", listOf(fourmi.copy(field = 1)))
        fourmizzz.players("202610201200", listOf(fourmi.copy(field = 2)))

        post(Exports.Players.Sync(version = "202610201100"))
        post(Exports.Players.Sync(version = "202610201200"))

        val rows = query("SELECT version, field FROM exported_players WHERE id = 123 ORDER BY version") {
            it.getString(0) to it.getLong(1)
        }
        assertEquals(listOf("202610201100" to 1L, "202610201200" to 2L), rows)
    }

    @Test
    fun `replaces the whole version when synced again`() = appTest {
        val sync = Exports.Players.Sync(version = "202610201200")
        fourmizzz.players("202610201200", listOf(fourmi, fourmi.copy(id = 124)))
        post(sync)

        fourmizzz.players("202610201200", listOf(fourmi.copy(pseudo = "renamed")))
        post(sync)

        val rows = query("SELECT id, pseudo FROM exported_players") { it.getLong(0) to it.getString(1) }
        assertEquals(listOf(123L to "renamed"), rows)
    }

    @Test
    fun `stores large exports`() = appTest {
        fourmizzz.players("202610201200", (1L..1_234L).map { fourmi.copy(id = it) })

        val res = post(Exports.Players.Sync())

        assertEquals(SyncResponse("202610201200", 1_234), res.body<SyncResponse>())
        assertEquals(listOf(1_234L), query("SELECT count(*) FROM exported_players") { it.getLong(0) })
    }

    @Test
    fun `rejects a malformed version with a JSON error`() = appTest {
        val res = post(Exports.Players.Sync(version = "2026-10-20"))

        assertEquals(HttpStatusCode.BadRequest, res.status)
        assertEquals(
            ErrorResponse(400, "Bad Request", "Export version must be \"latest\" or a yyyyMMddHHmm date, got 2026-10-20"),
            res.body<ErrorResponse>(),
        )
    }

    @Test
    fun `rejects an impossible date without asking Fourmizzz`() = appTest {
        fourmizzz.players("202602300000", listOf(fourmi))

        for (version in listOf("202602300000", "202613010000", "202610202400", "202610201260")) {
            assertEquals(HttpStatusCode.BadRequest, post(Exports.Players.Sync(version = version)).status, version)
        }
        assertEquals(listOf(0L), query("SELECT count(*) FROM exported_players") { it.getLong(0) })
    }

    @Test
    fun `returns 404 when Fourmizzz has no export for the version`() = appTest {
        assertEquals(HttpStatusCode.NotFound, post(Exports.Players.Sync(version = "202001010000")).status)
    }

    @Test
    fun `returns 502 when Fourmizzz answers with an unexpected format`() = appTest {
        fourmizzz[ExportType.PLAYERS, "202610201200"] = buildJsonArray { add(buildJsonObject { put("id", "not-a-number") }) }

        assertEquals(HttpStatusCode.BadGateway, post(Exports.Players.Sync()).status)
    }

    @Test
    fun `returns 502 when Fourmizzz fails`() = appTest {
        fourmizzz.players("202610201200", listOf(fourmi))
        fourmizzz.failing = true

        val res = post(Exports.Players.Sync(version = "202610201200"))

        assertEquals(HttpStatusCode.BadGateway, res.status)
        assertEquals(502, res.body<ErrorResponse>().statusCode)
    }

    @Test
    fun `returns 502 when Fourmizzz is unreachable`() = appTest(fourmizzzUrl = "http://unreachable.test") {
        val res = post(Exports.Players.Sync(version = "202610201200"))

        assertEquals(HttpStatusCode.BadGateway, res.status)
        assertEquals("Fourmizzz /api/exports/players/ is unavailable", res.body<ErrorResponse>().message)
    }
}
