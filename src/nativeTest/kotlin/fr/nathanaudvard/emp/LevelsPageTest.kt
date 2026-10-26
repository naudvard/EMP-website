package fr.nathanaudvard.emp

import fr.nathanaudvard.emp.api.resources.Exports
import fr.nathanaudvard.emp.web.resources.Levels
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

class LevelsPageTest {
    private suspend fun AppTestScope.syncMembers() {
        fourmizzz.players("202610201100", listOf(fourmi.copy(id = 7, pseudo = "Ancien")))
        fourmizzz.players(
            "202610201200",
            listOf(fourmi, fourmi.copy(id = 124, pseudo = "abeille"), fourmi.copy(id = 125, pseudo = "Intrus", alliance = "LRM")),
        )
        sync(Exports.Players.Sync(version = "202610201100"))
        sync(Exports.Players.Sync(version = "202610201200"))
    }

    private suspend fun AppTestScope.submit(pseudo: String, kind: String, level: String): HttpResponse =
        client.submitForm(
            "/update-levels",
            parameters {
                append("pseudo", pseudo)
                append("kind", kind)
                append("level", level)
            },
        )

    private suspend fun AppTestScope.storedLevels() =
        // Synced members have a row without any level
        query(
            "SELECT id, incubator_level, solarium_level, laying_technique_level FROM players " +
                "WHERE coalesce(incubator_level, solarium_level, laying_technique_level, aphid_farm_level) IS NOT NULL",
        ) { cursor ->
            (0..3).map { cursor.getLong(it) }
        }

    @Test
    fun `lists the alliance members of the latest synced export`() = appTest {
        syncMembers()

        val page = client.get(Levels()).bodyAsText()

        val datalist = Regex("""<datalist id="members">(.*?)</datalist>""", RegexOption.DOT_MATCHES_ALL).find(page)!!.groupValues[1]
        assertEquals(listOf("abeille", "Fourmi"), Regex("""value="([^"]*)"""").findAll(datalist).map { it.groupValues[1] }.toList())
    }

    @Test
    fun `saves a kind level and keeps the other ones`() = appTest {
        syncMembers()

        val res = submit("Fourmi", "INCUBATOR", "12")
        submit("Fourmi", "SOLARIUM", "8")
        submit("Fourmi", "INCUBATOR", "13")

        assertEquals(HttpStatusCode.Found, res.status)
        val location = res.headers[HttpHeaders.Location]!!
        assertTrue("Niveau de Fourmi enregistré." in client.get(location).bodyAsText())
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
    fun `rejects an unknown kind or a level that is not a positive integer`() = appTest {
        syncMembers()

        val invalid = listOf(
            "GRENIER" to "3", "INCUBATOR" to "-1", "INCUBATOR" to "1.5", "INCUBATOR" to "", "INCUBATOR" to "abc",
            "INCUBATOR" to "41", "SOLARIUM" to "1000", "INCUBATOR" to "99999999999",
        )
        for ((kind, level) in invalid) {
            assertEquals(HttpStatusCode.BadRequest, submit("Fourmi", kind, level).status, "$kind $level")
        }
        assertEquals(emptyList(), storedLevels())
    }

    @Test
    fun `keeps the submitted values when the form is invalid`() = appTest {
        syncMembers()

        val page = submit("Fourmi", "SOLARIUM", "-1").bodyAsText()

        assertTrue("""value="Fourmi"""" in page)
        assertTrue(Regex("""<option value="SOLARIUM" selected""").containsMatchIn(page))
        assertTrue("Le niveau doit être un entier entre 0 et 40." in page)
    }

    @Test
    fun `accepts the highest level`() = appTest {
        syncMembers()

        assertEquals(HttpStatusCode.Found, submit("Fourmi", "INCUBATOR", "40").status)
        assertEquals(listOf(listOf(123L, 40L, null, null)), storedLevels())
    }

    @Test
    fun `confirms a save only for a member pseudo`() = appTest {
        syncMembers()

        assertTrue("Niveau de Fourmi enregistré." in client.get(Levels(saved = "Fourmi")).bodyAsText())
        assertTrue("enregistré." !in client.get(Levels(saved = "Intrus")).bodyAsText())
    }

    @Test
    fun `keeps the pseudo filled in after a save`() = appTest {
        syncMembers()

        assertTrue("""value="Fourmi"""" in client.get(Levels(saved = "Fourmi")).bodyAsText())
        assertTrue("""value="Intrus"""" !in client.get(Levels(saved = "Intrus")).bodyAsText())
    }
}
