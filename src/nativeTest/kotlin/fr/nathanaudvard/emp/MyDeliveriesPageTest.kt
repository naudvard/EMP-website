package fr.nathanaudvard.emp

import fr.nathanaudvard.emp.web.pages.NBSP
import fr.nathanaudvard.emp.api.resources.Alliances
import fr.nathanaudvard.emp.api.resources.Exports
import fr.nathanaudvard.emp.web.resources.MyDeliveries
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class MyDeliveriesPageTest {
    // Other player and amount of each delivery listed
    private fun rows(page: String) =
        Regex("""<li(?: class="done")?>\s*<span class="pseudo">([^<]*)</span>\s*<span class="figures">\s*<span class="amount [a-z]+">([^<]*)</span>""")
            .findAll(page).map { it.destructured.toList() }.toList()

    // Other player and progress of each delivery that has one
    private fun progress(page: String) =
        Regex("""<span class="pseudo">([^<]*)</span>\s*<span class="figures">\s*<span class="amount [a-z]+">[^<]*</span>\s*<span class="progress[a-z ]*">([^<]*)</span>""")
            .findAll(page).map { it.destructured.toList() }.toList()

    // Recipient, amount and date of each convoy recorded, the latest first
    private fun convoys(page: String) =
        Regex("""<li>\s*<span class="pseudo">([^<]*)</span>\s*<span class="amount give">([^<]*)</span>\s*<span class="when">([^<]*)</span>""")
            .findAll(page).map { it.destructured.toList() }.toList()

    private suspend fun AppTestScope.send(pseudo: String, message: String): HttpResponse =
        client.submitForm("/my-deliveries?pseudo=$pseudo", parameters { append("message", message) })

    // P1 and P2 deliver to P3 and P4, unless P2 accumulates debt instead
    private suspend fun AppTestScope.computeTwice(debtor: Boolean = false) {
        val fields = mapOf(1L to 4_000L, 2L to 3_000L, 3L to 500L, 4L to 1_000L)
        val players = fields.map { (id, field) -> fourmi.copy(id = id, pseudo = "P$id", field = field) }
        clock.now = NOW - 24.hours
        fourmizzz.players("202610191200", players)
        sync(Exports.Players.Sync())
        if (debtor) execute("UPDATE players SET wage_settlement = 'ACCUMULATE_DEBT' WHERE id = 2")
        post(Alliances.Tag.Wages(Alliances.Tag(tag = "EMP"), distribution = "minimal"))
        clock.now = NOW
        fourmizzz.players("202610201200", players)
        post(Alliances.Tag.Wages(Alliances.Tag(tag = "EMP"), distribution = "minimal"))
        signIn("P3")
    }

    @Test
    fun `says when no wages have been computed yet`() = appTest {
        syncAndSignIn()
        val res = client.get(MyDeliveries(pseudo = "P1"))

        assertEquals(HttpStatusCode.OK, res.status)
        assertTrue("Aucun calcul de salaires pour l'instant." in res.bodyAsText())
    }

    @Test
    fun `shows what the player sends in the latest computation`() = appTest {
        computeTwice()

        val page = client.get(MyDeliveries(pseudo = "p1")).bodyAsText()

        assertTrue("D'après le dernier calcul, le mar. 20${NBSP}oct. 14:30." in page)
        assertTrue("""<strong class="amount give">90 000 bois</strong>à 2 joueurs""" in page)
        assertEquals(listOf(listOf("→ P3", "78 000"), listOf("→ P4", "12 000")), rows(page))
        assertTrue("""<b class="give">−90 000</b>""" in page)
        assertFalse("Plus ancien" in page)
    }

    @Test
    fun `shows what the player receives`() = appTest {
        computeTwice()

        val page = client.get(MyDeliveries(pseudo = "P4")).bodyAsText()

        assertTrue("""<strong class="amount receive">54 000 bois</strong>de 2 joueurs""" in page)
        assertEquals(listOf(listOf("← P2", "42 000"), listOf("← P1", "12 000")), rows(page))
    }

    @Test
    fun `shows the debt left to the player`() = appTest {
        computeTwice(debtor = true)

        val page = client.get(MyDeliveries(pseudo = "P2")).bodyAsText()

        assertTrue("Dette restante : <b>84 000</b>" in page)
    }

    @Test
    fun `says when the pseudo is not in the latest computation`() = appTest {
        computeTwice()

        val page = client.get(MyDeliveries(pseudo = "Inconnu")).bodyAsText()

        assertTrue("Inconnu n'est pas dans le dernier calcul." in page)
        assertTrue("""value="Inconnu"""" in page)
    }

    @Test
    fun `navigation links to the page after the levels`() = appTest {
        syncAndSignIn()
        val page = client.get(MyDeliveries()).bodyAsText()

        assertTrue("""<a href="/update-levels" class="emp-tab">Niveaux</a><a href="/my-deliveries" class="emp-tab" aria-current="page">Envois</a>""" in page.replace(Regex(">\\s+<"), "><"))
    }

    @Test
    fun `records the convoys pasted from the game and shows what is left to deliver`() = appTest {
        computeTwice()

        val res = send("P1", "Vous allez livrer 0 nourritures et 50 000 materiaux à P3 dans 10H 46m 4s - Retour le 21 oct. à 08h39 - Annuler")

        assertEquals(HttpStatusCode.Found, res.status)
        val page = client.get(res.headers[HttpHeaders.Location]!!).bodyAsText()
        assertTrue("Convoi enregistré." in page)
        assertEquals(listOf(listOf("→ P3", "50 000 envoyés, reste 28 000")), progress(page))
        assertEquals(listOf(listOf("→ P3", "50 000", "mar. 20${NBSP}oct. 14:30")), convoys(page))
    }

    @Test
    fun `adds every convoy to the ones recorded before`() = appTest {
        computeTwice()

        send("P1", "Vous allez livrer 0 nourritures et 50 000 materiaux à P3 dans 10H 46m 4s - Retour le 21 oct. à 08h39 - Annuler")
        clock.now = NOW + 5.minutes
        // Several lines at once, copied with non-breaking spaces and accents
        send(
            "P1",
            "Vous allez livrer 0 nourritures et 28\u00a0000 matériaux à p3 dans 1H - Annuler\n" +
                "Vous allez livrer 100 nourritures et 12 000 materiaux à P4 dans 2H - Annuler",
        )

        val page = client.get(MyDeliveries(pseudo = "P1")).bodyAsText()
        assertEquals(listOf(listOf("→ P3", "✓ envoyé"), listOf("→ P4", "✓ envoyé")), progress(page))
        assertEquals(
            listOf(
                listOf("→ P4", "12 000", "mar. 20${NBSP}oct. 14:35"),
                listOf("→ P3", "28 000", "mar. 20${NBSP}oct. 14:35"),
                listOf("→ P3", "50 000", "mar. 20${NBSP}oct. 14:30"),
            ),
            convoys(page),
        )
        assertTrue("""<span class="progress done">✓ envoyé</span>""" in page)
    }

    @Test
    fun `shows the recipient what was sent to them`() = appTest {
        computeTwice()
        send("P1", "Vous allez livrer 0 nourritures et 2 000 materiaux à P4 dans 1H - Annuler")

        val page = client.get(MyDeliveries(pseudo = "P4")).bodyAsText()

        assertEquals(listOf(listOf("← P1", "2 000 envoyés, reste 10 000")), progress(page))
        assertTrue(convoys(page).isEmpty())
    }

    @Test
    fun `rejects a message without a convoy of materials`() = appTest {
        computeTwice()

        for (message in listOf("Bonjour", "Vous allez livrer 500 nourritures et 0 materiaux à P3 dans 1H - Annuler")) {
            val res = send("P1", message)

            assertEquals(HttpStatusCode.BadRequest, res.status)
            assertTrue("Aucun convoi de matériaux reconnu." in res.bodyAsText())
        }
        assertTrue(query("SELECT id FROM convoys") { it.getLong(0) }.isEmpty())
    }

    @Test
    fun `rejects a convoy towards no planned delivery`() = appTest {
        computeTwice()

        val unknown = send("P1", "Vous allez livrer 0 nourritures et 1 000 materiaux à Inconnu dans 1H - Annuler")
        val unplanned = send(
            "P1",
            "Vous allez livrer 0 nourritures et 1 000 materiaux à P3 dans 1H - Annuler\n" +
                "Vous allez livrer 0 nourritures et 1 000 materiaux à P2 dans 1H - Annuler",
        )

        assertEquals(HttpStatusCode.BadRequest, unknown.status)
        assertTrue("Inconnu n'est pas dans le dernier calcul." in unknown.bodyAsText())
        assertEquals(HttpStatusCode.BadRequest, unplanned.status)
        val page = unplanned.bodyAsText()
        assertTrue("Aucune livraison n'est prévue de P1 à P2." in page)
        // The message stays to be corrected
        assertTrue("à P2 dans 1H - Annuler</textarea>" in page)
        assertTrue(query("SELECT id FROM convoys") { it.getLong(0) }.isEmpty())
    }

    @Test
    fun `removes a convoy only for the player who sent it or a chief`() = appTest {
        computeTwice()
        send("P1", "Vous allez livrer 0 nourritures et 50 000 materiaux à P3 dans 1H - Annuler")
        send("P1", "Vous allez livrer 0 nourritures et 2 000 materiaux à P4 dans 1H - Annuler")
        val (first, second) = query("SELECT id FROM convoys ORDER BY id") { it.getLong(0)!! }

        signIn("P2", chief = false)
        client.submitForm("/my-deliveries/convoys/$first/delete?pseudo=P2", parameters { })
        val forbidden = client.submitForm("/my-deliveries/convoys/$first/delete?pseudo=P1", parameters { })
        assertEquals(HttpStatusCode.Forbidden, forbidden.status)
        assertEquals(2, query("SELECT id FROM convoys") { it.getLong(0) }.size)

        signIn("P1", chief = false)
        val res = client.submitForm("/my-deliveries/convoys/$first/delete?pseudo=P1", parameters { })
        assertEquals(HttpStatusCode.Found, res.status)
        assertEquals("/my-deliveries?pseudo=P1#convoys", res.headers[HttpHeaders.Location])
        assertEquals(listOf(listOf("→ P4", "2 000 envoyés, reste 10 000")), progress(client.get(MyDeliveries()).bodyAsText()))

        signIn("P3")
        client.submitForm("/my-deliveries/convoys/$second/delete?pseudo=P1", parameters { })
        assertTrue(query("SELECT id FROM convoys") { it.getLong(0) }.isEmpty())
    }

    @Test
    fun `a member sees everyone's deliveries and records their own convoys only`() = appTest {
        computeTwice()
        signIn("P2", chief = false)

        val own = client.get(MyDeliveries()).bodyAsText()
        val other = client.get(MyDeliveries(pseudo = "P1")).bodyAsText()
        val forbidden = send("P1", "Vous allez livrer 0 nourritures et 50 000 materiaux à P3 dans 1H - Annuler")

        assertTrue("<h1>Mes livraisons</h1>" in own)
        assertEquals(listOf(listOf("→ P4", "42 000")), rows(own))
        assertTrue("""class="convoy-form"""" in own)
        assertTrue("""name="pseudo"""" !in own)
        assertTrue("<h1>Livraisons de P1</h1>" in other)
        assertEquals(listOf(listOf("→ P3", "78 000"), listOf("→ P4", "12 000")), rows(other))
        assertTrue("""class="convoy-form"""" !in other)
        assertTrue("Supprimer" !in other)
        assertEquals(HttpStatusCode.Forbidden, forbidden.status)
        assertTrue(query("SELECT id FROM convoys") { it.getLong(0) }.isEmpty())
    }
}