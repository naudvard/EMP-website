package fr.nathanaudvard.emp

import fr.nathanaudvard.emp.domain.model.ExportType
import fr.nathanaudvard.emp.input.http.dto.ErrorResponse
import fr.nathanaudvard.emp.input.http.dto.PlayerResponse
import fr.nathanaudvard.emp.input.http.routes.ApiV1
import io.ktor.client.call.body
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

class PlayerRoutesTest {
    @Test
    fun `stores the requested version keeping nullable fields and booleans`() = syncTest {
        val unaligned = fourmi.copy(id = 124, alliance = null, grade = null, masterPlayerId = 123, onHoliday = true)
        fourmizzz.players("202610201200", listOf(fourmi, unaligned))

        val res = post(ApiV1.Players.Sync(version = "202610201200"))

        assertEquals(HttpStatusCode.OK, res.status)
        val players = Json.parseToJsonElement(res.bodyAsText()).jsonArray
        assertEquals(2, players.size)
        val expected = Json.encodeToJsonElement(unaligned).jsonObject + ("version" to JsonPrimitive("202610201200"))
        assertEquals(JsonObject(expected), players.single { it.jsonObject["id"]?.jsonPrimitive?.long == 124L })
    }

    @Test
    fun `stores the latest export under its real Fourmizzz version`() = syncTest {
        fourmizzz.players("202610201100", listOf(fourmi.copy(pseudo = "old")))
        fourmizzz.players("202610201200", listOf(fourmi.copy(pseudo = "new")))

        val stored = post(ApiV1.Players.Sync()).body<List<PlayerResponse>>().single()

        assertEquals("202610201200", stored.version)
        assertEquals("new", stored.pseudo)
    }

    @Test
    fun `keeps each version separately`() = syncTest {
        fourmizzz.players("202610201100", listOf(fourmi.copy(field = 1)))
        fourmizzz.players("202610201200", listOf(fourmi.copy(field = 2)))

        post(ApiV1.Players.Sync(version = "202610201100"))
        post(ApiV1.Players.Sync(version = "202610201200"))

        val rows = query("SELECT version, field FROM players WHERE id = 123 ORDER BY version") {
            it.getString(0) to it.getLong(1)
        }
        assertEquals(listOf("202610201100" to 1L, "202610201200" to 2L), rows)
    }

    @Test
    fun `replaces the whole version when synced again`() = syncTest {
        val sync = ApiV1.Players.Sync(version = "202610201200")
        fourmizzz.players("202610201200", listOf(fourmi, fourmi.copy(id = 124)))
        post(sync)

        fourmizzz.players("202610201200", listOf(fourmi.copy(pseudo = "renamed")))
        post(sync)

        val rows = query("SELECT id, pseudo FROM players") { it.getLong(0) to it.getString(1) }
        assertEquals(listOf(123L to "renamed"), rows)
    }

    @Test
    fun `stores large exports`() = syncTest {
        fourmizzz.players("202610201200", (1L..1_234L).map { fourmi.copy(id = it) })

        val res = post(ApiV1.Players.Sync())

        assertEquals(HttpStatusCode.OK, res.status)
        assertEquals(1_234, res.body<List<PlayerResponse>>().size)
        assertEquals(listOf(1_234L), query("SELECT count(*) FROM players") { it.getLong(0) })
    }

    @Test
    fun `rejects a malformed version with a JSON error`() = syncTest {
        val res = post(ApiV1.Players.Sync(version = "2026-10-20"))

        assertEquals(HttpStatusCode.BadRequest, res.status)
        assertEquals(
            ErrorResponse(400, "Bad Request", "Export version must be \"latest\" or a yyyyMMddHHmm date, got 2026-10-20"),
            res.body<ErrorResponse>(),
        )
    }

    @Test
    fun `returns 404 when Fourmizzz has no export for the version`() = syncTest {
        assertEquals(HttpStatusCode.NotFound, post(ApiV1.Players.Sync(version = "202001010000")).status)
    }

    @Test
    fun `returns 502 when Fourmizzz answers with an unexpected format`() = syncTest {
        fourmizzz[ExportType.PLAYERS, "202610201200"] = buildJsonArray { add(buildJsonObject { put("id", "not-a-number") }) }

        assertEquals(HttpStatusCode.BadGateway, post(ApiV1.Players.Sync()).status)
    }

    @Test
    fun `returns 502 when Fourmizzz fails`() = syncTest {
        fourmizzz.players("202610201200", listOf(fourmi))
        fourmizzz.failing = true

        val res = post(ApiV1.Players.Sync(version = "202610201200"))

        assertEquals(HttpStatusCode.BadGateway, res.status)
        assertEquals(502, res.body<ErrorResponse>().statusCode)
    }

    @Test
    fun `returns 502 when Fourmizzz is unreachable`() = syncTest(fourmizzzUrl = "http://unreachable.test") {
        val res = post(ApiV1.Players.Sync(version = "202610201200"))

        assertEquals(HttpStatusCode.BadGateway, res.status)
        assertEquals("Fourmizzz /api/exports/players/ is unavailable", res.body<ErrorResponse>().message)
    }
}
