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
        val colony = fourmi.copy(id = 124, grade = null, masterPlayerId = 123, onHoliday = true)
        fourmizzz.players("202610201200", listOf(fourmi, colony))

        val res = sync(Exports.Players.Sync(version = "202610201200"))

        assertEquals(HttpStatusCode.OK, res.status)
        assertEquals(SyncResponse("202610201200", 2), res.body<SyncResponse>())
        val rows = query(
            "SELECT version, id, alliance_tag, master_player_id, grade, on_holiday, is_banned FROM exported_players ORDER BY id",
        ) { (0..6).map { i -> it.getString(i) } }
        assertEquals(
            listOf(
                listOf("202610201200", "123", "EMP", null, "Chef", "0", "0"),
                listOf("202610201200", "124", "EMP", "123", null, "1", "0"),
            ),
            rows,
        )
    }

    @Test
    fun `stores the latest export under its real Fourmizzz version`() = appTest {
        fourmizzz.players("202610201100", listOf(fourmi.copy(pseudo = "old")))
        fourmizzz.players("202610201200", listOf(fourmi.copy(pseudo = "new")))

        val res = sync(Exports.Players.Sync())

        assertEquals(SyncResponse("202610201200", 1), res.body<SyncResponse>())
        val rows = query("SELECT version, pseudo FROM exported_players") { it.getString(0) to it.getString(1) }
        assertEquals(listOf("202610201200" to "new"), rows)
    }

    @Test
    fun `keeps each version separately`() = appTest {
        fourmizzz.players("202610201100", listOf(fourmi.copy(field = 1)))
        fourmizzz.players("202610201200", listOf(fourmi.copy(field = 2)))

        sync(Exports.Players.Sync(version = "202610201100"))
        sync(Exports.Players.Sync(version = "202610201200"))

        val rows = query("SELECT version, field FROM exported_players WHERE id = 123 ORDER BY version") {
            it.getString(0) to it.getLong(1)
        }
        assertEquals(listOf("202610201100" to 1L, "202610201200" to 2L), rows)
    }

    @Test
    fun `replaces the whole version when synced again`() = appTest {
        val request = Exports.Players.Sync(version = "202610201200")
        fourmizzz.players("202610201200", listOf(fourmi, fourmi.copy(id = 124)))
        sync(request)

        fourmizzz.players("202610201200", listOf(fourmi.copy(pseudo = "renamed")))
        sync(request)

        val rows = query("SELECT id, pseudo FROM exported_players") { it.getLong(0) to it.getString(1) }
        assertEquals(listOf(123L to "renamed"), rows)
    }

    @Test
    fun `stores the members of the requested alliances only`() = appTest {
        val players = listOf(
            fourmi,
            fourmi.copy(id = 124, alliance = "LRM"),
            fourmi.copy(id = 125, alliance = "ODF"),
            fourmi.copy(id = 126, alliance = null),
        )
        fourmizzz.players("202610201200", players)

        val res = sync(Exports.Players.Sync(), tags = listOf("EMP", "LRM"))

        assertEquals(SyncResponse("202610201200", 2), res.body<SyncResponse>())
        val rows = query("SELECT id, alliance_tag FROM exported_players ORDER BY id") { it.getLong(0) to it.getString(1) }
        assertEquals(listOf(123L to "EMP", 124L to "LRM"), rows)
        val syncs = query("SELECT version, alliance_tag FROM exported_player_syncs ORDER BY alliance_tag") {
            it.getString(0) to it.getString(1)
        }
        assertEquals(listOf("202610201200" to "EMP", "202610201200" to "LRM"), syncs)
    }

    @Test
    fun `keeps the other alliances of the version when synced for another tag`() = appTest {
        val request = Exports.Players.Sync(version = "202610201200")
        fourmizzz.players("202610201200", listOf(fourmi, fourmi.copy(id = 124, alliance = "LRM")))
        sync(request, tags = listOf("EMP"))

        fourmizzz.players("202610201200", listOf(fourmi.copy(pseudo = "renamed"), fourmi.copy(id = 124, alliance = "LRM")))
        sync(request, tags = listOf("LRM"))

        val rows = query("SELECT id, pseudo FROM exported_players ORDER BY id") { it.getLong(0) to it.getString(1) }
        assertEquals(listOf(123L to "Fourmi", 124L to "Fourmi"), rows)
    }

    @Test
    fun `records an alliance without members in the version`() = appTest {
        fourmizzz.players("202610201200", listOf(fourmi))

        val res = sync(Exports.Players.Sync(), tags = listOf("XYZ"))

        assertEquals(SyncResponse("202610201200", 0), res.body<SyncResponse>())
        assertEquals(listOf("XYZ"), query("SELECT alliance_tag FROM exported_player_syncs") { it.getString(0) })
    }

    @Test
    fun `requires alliance tags`() = appTest {
        fourmizzz.players("202610201200", listOf(fourmi))

        assertEquals(HttpStatusCode.BadRequest, sync(Exports.Players.Sync(), tags = emptyList()).status)
        assertEquals(HttpStatusCode.BadRequest, sync(Exports.Players.Sync(), tags = listOf(" ")).status)
        assertEquals(HttpStatusCode.UnsupportedMediaType, post(Exports.Players.Sync()).status)
        assertEquals(listOf(0L), query("SELECT count(*) FROM exported_players") { it.getLong(0) })
    }

    @Test
    fun `stores large exports`() = appTest {
        fourmizzz.players("202610201200", (1L..1_234L).map { fourmi.copy(id = it) })

        val res = sync(Exports.Players.Sync())

        assertEquals(SyncResponse("202610201200", 1_234), res.body<SyncResponse>())
        assertEquals(listOf(1_234L), query("SELECT count(*) FROM exported_players") { it.getLong(0) })
    }

    @Test
    fun `rejects a malformed version with a JSON error`() = appTest {
        val res = sync(Exports.Players.Sync(version = "2026-10-20"))

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
            assertEquals(HttpStatusCode.BadRequest, sync(Exports.Players.Sync(version = version)).status, version)
        }
        assertEquals(listOf(0L), query("SELECT count(*) FROM exported_players") { it.getLong(0) })
    }

    @Test
    fun `returns 404 when Fourmizzz has no export for the version`() = appTest {
        assertEquals(HttpStatusCode.NotFound, sync(Exports.Players.Sync(version = "202001010000")).status)
        assertEquals(1, fourmizzz.exportRequests)
    }

    @Test
    fun `returns 502 when Fourmizzz answers with an unexpected format`() = appTest {
        fourmizzz[ExportType.PLAYERS, "202610201200"] = buildJsonArray { add(buildJsonObject { put("id", "not-a-number") }) }

        assertEquals(HttpStatusCode.BadGateway, sync(Exports.Players.Sync()).status)
    }

    @Test
    fun `returns 502 when Fourmizzz fails`() = appTest {
        fourmizzz.players("202610201200", listOf(fourmi))
        fourmizzz.failing = true

        val res = sync(Exports.Players.Sync(version = "202610201200"))

        assertEquals(HttpStatusCode.BadGateway, res.status)
        assertEquals(502, res.body<ErrorResponse>().statusCode)
        assertEquals(3, fourmizzz.exportRequests)
    }

    @Test
    fun `asks Fourmizzz again after a passing failure`() = appTest {
        fourmizzz.players("202610201200", listOf(fourmi))
        fourmizzz.failuresLeft = 2

        val res = sync(Exports.Players.Sync(version = "202610201200"))

        assertEquals(HttpStatusCode.OK, res.status)
        assertEquals(3, fourmizzz.exportRequests)
    }

    @Test
    fun `returns 502 when Fourmizzz is unreachable`() = appTest(fourmizzzUrl = "http://unreachable.test") {
        val res = sync(Exports.Players.Sync(version = "202610201200"))

        assertEquals(HttpStatusCode.BadGateway, res.status)
        assertEquals("Fourmizzz /api/exports/players/ is unavailable", res.body<ErrorResponse>().message)
    }
}
