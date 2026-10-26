package fr.nathanaudvard.emp

import fr.nathanaudvard.emp.api.dto.SyncResponse
import fr.nathanaudvard.emp.api.resources.Exports
import fr.nathanaudvard.emp.domain.model.ExportType
import fr.nathanaudvard.emp.fourmizzz.DiplomacyRes
import fr.nathanaudvard.emp.fourmizzz.PactRes
import io.ktor.client.call.body
import io.ktor.client.plugins.resources.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class SyncExportedAlliancesTest {
    @Test
    fun `stores the requested version with its pacts`() = appTest {
        fourmizzz.alliances("202610201200", listOf(empire))

        val res = sync(Exports.Alliances.Sync(version = "202610201200"))

        assertEquals(HttpStatusCode.OK, res.status)
        assertEquals(SyncResponse("202610201200", 1), res.body<SyncResponse>())
        val alliances = query("SELECT version, tag, players_count, wars FROM exported_alliances") {
            listOf(it.getString(0), it.getString(1), it.getLong(2), it.getString(3))
        }
        assertEquals(listOf(listOf("202610201200", "EMP", 12L, """["LRM"]""")), alliances)
        assertEquals(listOf("ODF"), query("SELECT tag FROM exported_alliance_pacts") { it.getString(0) })
    }

    @Test
    fun `stores the latest export under its real Fourmizzz version`() = appTest {
        fourmizzz.alliances("202610201100", listOf(empire.copy(name = "old")))
        fourmizzz.alliances("202610201200", listOf(empire.copy(name = "new")))

        val responses = listOf(
            client.post("/api/v1/exports/alliances/sync") {
                contentType(ContentType.Application.Json)
                setBody("""{"tags":["EMP"]}""")
            },
            sync(Exports.Alliances.Sync(version = "latest")),
        )

        for (res in responses) {
            assertEquals(SyncResponse("202610201200", 1), res.body<SyncResponse>())
        }
        val rows = query("SELECT version, name FROM exported_alliances") { it.getString(0) to it.getString(1) }
        assertEquals(listOf("202610201200" to "new"), rows)
    }

    @Test
    fun `replaces the whole version with its pacts when synced again`() = appTest {
        val request = Exports.Alliances.Sync(version = "202610201200")
        fourmizzz.alliances("202610201200", listOf(empire, empire.copy(tag = "LRM")))
        sync(request, tags = listOf("EMP", "LRM"))

        val pact = PactRes(tag = "TK", name = "PE", description = "pacte")
        fourmizzz.alliances(
            "202610201200",
            listOf(empire.copy(playersCount = 13, diplomacy = DiplomacyRes(pacts = listOf(pact), wars = emptyList()))),
        )
        sync(request, tags = listOf("EMP", "LRM"))

        val rows = query("SELECT tag, players_count FROM exported_alliances") { it.getString(0) to it.getLong(1) }
        assertEquals(listOf("EMP" to 13L), rows)
        assertEquals(listOf("TK"), query("SELECT tag FROM exported_alliance_pacts") { it.getString(0) })
    }

    @Test
    fun `stores the requested alliances only and keeps the others of the version`() = appTest {
        val request = Exports.Alliances.Sync(version = "202610201200")
        fourmizzz.alliances("202610201200", listOf(empire, empire.copy(tag = "LRM"), empire.copy(tag = "ODF")))

        assertEquals(SyncResponse("202610201200", 1), sync(request, tags = listOf("EMP")).body<SyncResponse>())
        sync(request, tags = listOf("LRM"))

        val tags = query("SELECT tag FROM exported_alliances ORDER BY tag") { it.getString(0) }
        assertEquals(listOf("EMP", "LRM"), tags)
        assertEquals(2, query("SELECT tag FROM exported_alliance_pacts") { it.getString(0) }.size)
    }

    @Test
    fun `requires alliance tags`() = appTest {
        fourmizzz.alliances("202610201200", listOf(empire))

        assertEquals(HttpStatusCode.BadRequest, sync(Exports.Alliances.Sync(), tags = emptyList()).status)
        assertEquals(HttpStatusCode.UnsupportedMediaType, post(Exports.Alliances.Sync()).status)
    }

    @Test
    fun `rejects a malformed version`() = appTest {
        assertEquals(HttpStatusCode.BadRequest, sync(Exports.Alliances.Sync(version = "abc")).status)
    }

    @Test
    fun `returns 400 when Fourmizzz rejects the version`() = appTest {
        fourmizzz.rejectedVersions += "202602280000"

        assertEquals(HttpStatusCode.BadRequest, sync(Exports.Alliances.Sync(version = "202602280000")).status)
    }

    @Test
    fun `returns 404 when Fourmizzz has no export for the version`() = appTest {
        assertEquals(HttpStatusCode.NotFound, sync(Exports.Alliances.Sync(version = "202001010000")).status)
    }

    @Test
    fun `returns 404 when Fourmizzz has no export at all`() = appTest {
        assertEquals(HttpStatusCode.NotFound, sync(Exports.Alliances.Sync()).status)
    }

    @Test
    fun `returns 502 when Fourmizzz answers with an unexpected format`() = appTest {
        fourmizzz[ExportType.ALLIANCES, "202610201200"] = buildJsonObject { put("unexpected", true) }

        assertEquals(HttpStatusCode.BadGateway, sync(Exports.Alliances.Sync()).status)
    }

    @Test
    fun `is not reachable with GET`() = appTest {
        assertEquals(HttpStatusCode.MethodNotAllowed, client.get(Exports.Alliances.Sync()).status)
    }
}
