package fr.nathanaudvard.emp

import fr.nathanaudvard.emp.api.dto.SyncResponse
import fr.nathanaudvard.emp.api.resources.Alliances
import fr.nathanaudvard.emp.domain.model.ExportType
import fr.nathanaudvard.emp.fourmizzz.DiplomacyRes
import fr.nathanaudvard.emp.fourmizzz.PactRes
import io.ktor.client.call.body
import io.ktor.client.plugins.resources.get
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class AllianceControllerTest {
    @Test
    fun `stores the requested version with its pacts`() = syncTest {
        fourmizzz.alliances("202610201200", listOf(empire))

        val res = post(Alliances.Sync(version = "202610201200"))

        assertEquals(HttpStatusCode.OK, res.status)
        assertEquals(SyncResponse("202610201200", 1), res.body<SyncResponse>())
        val alliances = query("SELECT version, tag, players_count, wars FROM alliances") {
            listOf(it.getString(0), it.getString(1), it.getLong(2), it.getString(3))
        }
        assertEquals(listOf(listOf("202610201200", "EMP", 12L, """["LRM"]""")), alliances)
        assertEquals(listOf("ODF"), query("SELECT tag FROM alliance_pacts") { it.getString(0) })
    }

    @Test
    fun `stores the latest export under its real Fourmizzz version`() = syncTest {
        fourmizzz.alliances("202610201100", listOf(empire.copy(name = "old")))
        fourmizzz.alliances("202610201200", listOf(empire.copy(name = "new")))

        val responses = listOf(
            client.post("/api/v1/alliances/sync"),
            post(Alliances.Sync(version = "latest")),
        )

        for (res in responses) {
            assertEquals(SyncResponse("202610201200", 1), res.body<SyncResponse>())
        }
        val rows = query("SELECT version, name FROM alliances") { it.getString(0) to it.getString(1) }
        assertEquals(listOf("202610201200" to "new"), rows)
    }

    @Test
    fun `replaces the whole version with its pacts when synced again`() = syncTest {
        val sync = Alliances.Sync(version = "202610201200")
        fourmizzz.alliances("202610201200", listOf(empire, empire.copy(tag = "LRM")))
        post(sync)

        val pact = PactRes(tag = "TK", name = "PE", description = "pacte")
        fourmizzz.alliances(
            "202610201200",
            listOf(empire.copy(playersCount = 13, diplomacy = DiplomacyRes(pacts = listOf(pact), wars = emptyList()))),
        )
        post(sync)

        val rows = query("SELECT tag, players_count FROM alliances") { it.getString(0) to it.getLong(1) }
        assertEquals(listOf("EMP" to 13L), rows)
        assertEquals(listOf("TK"), query("SELECT tag FROM alliance_pacts") { it.getString(0) })
    }

    @Test
    fun `rejects a malformed version`() = syncTest {
        assertEquals(HttpStatusCode.BadRequest, post(Alliances.Sync(version = "abc")).status)
    }

    @Test
    fun `returns 400 when Fourmizzz rejects the version`() = syncTest {
        fourmizzz.rejectedVersions += "202602300000"

        assertEquals(HttpStatusCode.BadRequest, post(Alliances.Sync(version = "202602300000")).status)
    }

    @Test
    fun `returns 404 when Fourmizzz has no export for the version`() = syncTest {
        assertEquals(HttpStatusCode.NotFound, post(Alliances.Sync(version = "202001010000")).status)
    }

    @Test
    fun `returns 404 when Fourmizzz has no export at all`() = syncTest {
        assertEquals(HttpStatusCode.NotFound, post(Alliances.Sync()).status)
    }

    @Test
    fun `returns 502 when Fourmizzz answers with an unexpected format`() = syncTest {
        fourmizzz[ExportType.ALLIANCES, "202610201200"] = buildJsonObject { put("unexpected", true) }

        assertEquals(HttpStatusCode.BadGateway, post(Alliances.Sync()).status)
    }

    @Test
    fun `is not reachable with GET`() = syncTest {
        assertEquals(HttpStatusCode.MethodNotAllowed, client.get(Alliances.Sync()).status)
    }
}
