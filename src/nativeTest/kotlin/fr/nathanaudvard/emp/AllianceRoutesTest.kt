package fr.nathanaudvard.emp

import fr.nathanaudvard.emp.domain.model.ExportType
import fr.nathanaudvard.emp.input.http.dto.AllianceResponse
import fr.nathanaudvard.emp.input.http.routes.ApiV1
import fr.nathanaudvard.emp.output.http.DiplomacyRes
import fr.nathanaudvard.emp.output.http.PactRes
import io.ktor.client.call.body
import io.ktor.client.plugins.resources.get
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class AllianceRoutesTest {
    @Test
    fun `stores the requested version with its pacts`() = syncTest {
        fourmizzz.alliances("202610201200", listOf(empire))

        val res = post(ApiV1.Alliances.Sync(version = "202610201200"))

        assertEquals(HttpStatusCode.OK, res.status)
        val alliance = res.body<List<AllianceResponse>>().single()
        assertEquals("202610201200", alliance.version)
        assertEquals("EMP", alliance.tag)
        assertEquals(listOf("LRM"), alliance.wars)
        assertEquals(listOf("ODF"), alliance.pacts.map { it.tag })
    }

    @Test
    fun `stores the latest export under its real Fourmizzz version`() = syncTest {
        fourmizzz.alliances("202610201100", listOf(empire.copy(name = "old")))
        fourmizzz.alliances("202610201200", listOf(empire.copy(name = "new")))

        val responses = listOf(
            client.post("/api/v1/alliances/sync"),
            post(ApiV1.Alliances.Sync(version = "latest")),
        )

        for (res in responses) {
            val alliance = res.body<List<AllianceResponse>>().single()
            assertEquals("202610201200", alliance.version)
            assertEquals("new", alliance.name)
        }
    }

    @Test
    fun `replaces pacts when the same version is synced again`() = syncTest {
        val sync = ApiV1.Alliances.Sync(version = "202610201200")
        fourmizzz.alliances("202610201200", listOf(empire))
        val first = post(sync).body<List<AllianceResponse>>()

        fourmizzz.alliances(
            "202610201200",
            listOf(
                empire.copy(
                    playersCount = 13,
                    diplomacy = DiplomacyRes(pacts = listOf(PactRes(tag = "TK", name = "PE", description = "pacte")), wars = emptyList()),
                ),
            ),
        )
        val second = post(sync).body<List<AllianceResponse>>()

        assertEquals(first.single().id, second.single().id)
        assertEquals(13, second.single().playersCount)
        assertEquals(listOf("TK"), query("SELECT tag FROM alliance_pacts") { it.getString(0) })
    }

    @Test
    fun `rejects a malformed version`() = syncTest {
        assertEquals(HttpStatusCode.BadRequest, post(ApiV1.Alliances.Sync(version = "abc")).status)
    }

    @Test
    fun `returns 400 when Fourmizzz rejects the version`() = syncTest {
        fourmizzz.rejectedVersions += "202602300000"

        assertEquals(HttpStatusCode.BadRequest, post(ApiV1.Alliances.Sync(version = "202602300000")).status)
    }

    @Test
    fun `returns 404 when Fourmizzz has no export for the version`() = syncTest {
        assertEquals(HttpStatusCode.NotFound, post(ApiV1.Alliances.Sync(version = "202001010000")).status)
    }

    @Test
    fun `returns 404 when Fourmizzz has no export at all`() = syncTest {
        assertEquals(HttpStatusCode.NotFound, post(ApiV1.Alliances.Sync()).status)
    }

    @Test
    fun `returns 502 when Fourmizzz answers with an unexpected format`() = syncTest {
        fourmizzz[ExportType.ALLIANCES, "202610201200"] = buildJsonObject { put("unexpected", true) }

        assertEquals(HttpStatusCode.BadGateway, post(ApiV1.Alliances.Sync()).status)
    }

    @Test
    fun `is not reachable with GET`() = syncTest {
        assertEquals(HttpStatusCode.MethodNotAllowed, client.get(ApiV1.Alliances.Sync()).status)
    }
}
