package fr.nathanaudvard.emp

import fr.nathanaudvard.emp.api.resources.Exports
import fr.nathanaudvard.emp.web.resources.Debts
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

class DebtsPageTest {
    private suspend fun AppTestScope.syncMembers() {
        fourmizzz.players(
            "202610201200",
            listOf(fourmi, fourmi.copy(id = 124, pseudo = "abeille"), fourmi.copy(id = 125, pseudo = "Intrus", alliance = "LRM")),
        )
        sync(Exports.Players.Sync(version = "202610201200"))
        signIn("Fourmi")
    }

    private suspend fun AppTestScope.submit(vararg choices: Pair<String, String>): HttpResponse =
        client.submitForm("/debts", parameters { choices.forEach { (name, value) -> append(name, value) } })

    private suspend fun AppTestScope.storedSettlements() =
        query("SELECT id, wage_settlement FROM players ORDER BY id") { it.getLong(0) to it.getString(1) }

    private fun checked(page: String) =
        Regex("""<input type="radio" name="player-(\d+)" value="(\w+)" checked""").findAll(page)
            .associate { it.groupValues[1].toLong() to it.groupValues[2] }

    @Test
    fun `members deliver until another choice is saved`() = appTest {
        syncMembers()

        val page = client.get(Debts()).bodyAsText()

        assertEquals(mapOf(124L to "DELIVER", 123L to "DELIVER"), checked(page))
        assertTrue("Intrus" !in page)
    }

    @Test
    fun `creates the synced members as delivering`() = appTest {
        syncMembers()

        assertEquals(listOf(123L to "DELIVER", 124L to "DELIVER"), storedSettlements())
    }

    @Test
    fun `keeps the saved choices and levels when members are synced again`() = appTest {
        syncMembers()
        submit("player-123" to "ACCUMULATE_DEBT")
        execute("UPDATE players SET incubator_level = 12 WHERE id = 123")

        syncMembers()

        assertEquals(listOf(123L to "ACCUMULATE_DEBT", 124L to "DELIVER"), storedSettlements())
        assertEquals(listOf(12L), query("SELECT incubator_level FROM players WHERE id = 123") { it.getLong(0) })
    }

    @Test
    fun `saves the choices and shows them back`() = appTest {
        syncMembers()

        val res = submit("player-123" to "ACCUMULATE_DEBT", "player-124" to "DELIVER")

        assertEquals(HttpStatusCode.Found, res.status)
        val page = client.get(res.headers[HttpHeaders.Location]!!).bodyAsText()
        assertTrue("Choix enregistrés." in page)
        assertEquals(mapOf(123L to "ACCUMULATE_DEBT", 124L to "DELIVER"), checked(page))
        assertEquals(listOf(123L to "ACCUMULATE_DEBT", 124L to "DELIVER"), storedSettlements())
    }

    @Test
    fun `keeps the saved levels of a player`() = appTest {
        syncMembers()
        client.submitForm(
            "/update-levels",
            parameters {
                append("pseudo", "Fourmi")
                append("kind", "INCUBATOR")
                append("level", "12")
            },
        )

        submit("player-123" to "ACCUMULATE_DEBT")
        submit("player-123" to "DELIVER")

        assertEquals(
            listOf(listOf<Any?>(12L, "DELIVER")),
            query("SELECT incubator_level, wage_settlement FROM players WHERE id = 123") { listOf(it.getLong(0), it.getString(1)) },
        )
    }

    @Test
    fun `skips players outside the alliance`() = appTest {
        syncMembers()

        val res = submit("player-123" to "ACCUMULATE_DEBT", "player-125" to "ACCUMULATE_DEBT", "player-abc" to "DELIVER")

        assertEquals(HttpStatusCode.Found, res.status)
        assertEquals(listOf(123L to "ACCUMULATE_DEBT", 124L to "DELIVER"), storedSettlements())
    }

    @Test
    fun `rejects an unknown choice without saving anything`() = appTest {
        syncMembers()

        val res = submit("player-123" to "ACCUMULATE_DEBT", "player-124" to "PAY_LATER")

        assertEquals(HttpStatusCode.BadRequest, res.status)
        assertTrue("Choisis « livrer » ou « cumuler la dette »" in res.bodyAsText())
        assertEquals(listOf(123L to "DELIVER", 124L to "DELIVER"), storedSettlements())
    }

    @Test
    fun `shows the chain rank of each member`() = appTest {
        fourmizzz.players(
            "202610201200",
            listOf(fourmi.copy(id = 1, pseudo = "Stonotwo", grade = "G1 - Le roc"), fourmi.copy(id = 2, pseudo = "Verso", grade = "Chef")),
        )
        sync(Exports.Players.Sync(version = "202610201200"))
        signIn("Verso")

        val page = client.get(Debts()).bodyAsText()

        val ranks = Regex("""<tr>\s*<td><span class="rank[^"]*">([^<]*)</span></td>\s*<td>([^<]*)</td>""").findAll(page)
            .map { it.groupValues[2] to it.groupValues[1] }
            .toList()
        assertEquals(listOf("Stonotwo" to "G1", "Verso" to "—"), ranks)
    }

    @Test
    fun `a member reads the debts and their own choice only`() = appTest {
        syncMembers()
        submit("player-124" to "ACCUMULATE_DEBT")
        signIn("abeille", chief = false)

        val page = client.get(Debts()).bodyAsText()

        assertTrue("Quand ton solde est négatif, tu cumules la dette." in page)
        assertTrue("<form" !in page)
        assertTrue("radio" !in page)
        assertTrue("Fourmi" in page)
    }

    @Test
    fun `a member cannot save the choices`() = appTest {
        syncMembers()
        signIn("abeille", chief = false)

        val res = submit("player-123" to "ACCUMULATE_DEBT", "player-124" to "ACCUMULATE_DEBT")

        assertEquals(HttpStatusCode.Forbidden, res.status)
        assertEquals(listOf(123L to "DELIVER", 124L to "DELIVER"), storedSettlements())
    }
}
