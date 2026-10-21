package fr.nathanaudvard.emp

import fr.nathanaudvard.emp.api.resources.Exports
import fr.nathanaudvard.emp.web.resources.LayingTime
import io.ktor.client.plugins.resources.get
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LayingTimePageTest {
    private suspend fun AppTestScope.syncMembers() {
        fourmizzz.players("202610201100", listOf(fourmi.copy(id = 7, pseudo = "Ancien")))
        fourmizzz.players(
            "202610201200",
            listOf(fourmi, fourmi.copy(id = 124, pseudo = "abeille"), fourmi.copy(id = 125, pseudo = "Intrus", alliance = "LRM")),
        )
        post(Exports.Players.Sync(version = "202610201100"))
        post(Exports.Players.Sync(version = "202610201200"))
    }

    private suspend fun AppTestScope.submit(pseudo: String, factor: String, level: String): HttpResponse =
        client.submitForm(
            "/tdp",
            parameters {
                append("pseudo", pseudo)
                append("factor", factor)
                append("level", level)
            },
        )

    private suspend fun AppTestScope.storedLevels() =
        query("SELECT id, incubator_level, solarium_level, laying_technique_level FROM players") { cursor ->
            (0..3).map { cursor.getLong(it) }
        }

    @Test
    fun `lists the alliance members of the latest synced export`() = appTest {
        syncMembers()

        val page = client.get(LayingTime()).bodyAsText()

        val datalist = Regex("""<datalist id="members">(.*?)</datalist>""", RegexOption.DOT_MATCHES_ALL).find(page)!!.groupValues[1]
        assertEquals(listOf("abeille", "Fourmi"), Regex("""value="([^"]*)"""").findAll(datalist).map { it.groupValues[1] }.toList())
    }

    @Test
    fun `saves a factor level and keeps the other ones`() = appTest {
        syncMembers()

        val res = submit("Fourmi", "INCUBATOR", "12")
        submit("Fourmi", "SOLARIUM", "8")
        submit("Fourmi", "INCUBATOR", "13")

        assertEquals(HttpStatusCode.Found, res.status)
        val location = res.headers[HttpHeaders.Location]!!
        assertTrue("TDP de Fourmi enregistré." in client.get(location).bodyAsText())
        assertEquals(listOf(listOf(123L, 13L, 8L, null)), storedLevels())
    }

    @Test
    fun `rejects a pseudo outside the latest alliance members`() = appTest {
        syncMembers()

        for (pseudo in listOf("Intrus", "Ancien", "Inconnu", "fourmi")) {
            val res = submit(pseudo, "INCUBATOR", "12")

            assertEquals(HttpStatusCode.BadRequest, res.status, pseudo)
            assertTrue("Choisis un pseudo" in res.bodyAsText(), pseudo)
        }
        assertEquals(emptyList(), storedLevels())
    }

    @Test
    fun `rejects an unknown factor or a level that is not a positive integer`() = appTest {
        syncMembers()

        for ((factor, level) in listOf("GRENIER" to "3", "INCUBATOR" to "-1", "INCUBATOR" to "1.5", "INCUBATOR" to "", "INCUBATOR" to "abc")) {
            assertEquals(HttpStatusCode.BadRequest, submit("Fourmi", factor, level).status, "$factor $level")
        }
        assertEquals(emptyList(), storedLevels())
    }

    @Test
    fun `keeps the submitted values when the form is invalid`() = appTest {
        syncMembers()

        val page = submit("Fourmi", "SOLARIUM", "-1").bodyAsText()

        assertTrue("""value="Fourmi"""" in page)
        assertTrue(Regex("""<option value="SOLARIUM" selected""").containsMatchIn(page))
        assertTrue("Le niveau doit être un entier positif ou nul." in page)
    }
}
