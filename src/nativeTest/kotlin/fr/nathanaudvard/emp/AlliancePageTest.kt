package fr.nathanaudvard.emp

import fr.nathanaudvard.emp.api.resources.Exports
import fr.nathanaudvard.emp.domain.model.WageSettlement
import fr.nathanaudvard.emp.web.pages.DIGIT_GROUP_SEPARATOR
import fr.nathanaudvard.emp.web.pages.NBSP
import fr.nathanaudvard.emp.web.resources.Alliance
import fr.nathanaudvard.emp.web.resources.Wages
import io.ktor.client.plugins.resources.get
import io.ktor.client.plugins.resources.post
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
import kotlin.time.Duration.Companion.days

class AlliancePageTest {
    private val abeille = fourmi.copy(id = 124, pseudo = "abeille", grade = "P5 - Hypnos", field = 60_963, masterPlayerId = 123)
    private val cigale = fourmi.copy(id = 125, pseudo = "Cigale", grade = "G1 - Le roc", field = 1_000)

    // Without the indentation between tags, with plain spaces in the amounts and dates
    private fun plain(page: String) =
        page.replace(Regex(">\\s+<"), "><").replace(DIGIT_GROUP_SEPARATOR, " ").replace(NBSP, " ")

    private val AppTestScope.browser get() = client.config { followRedirects = false }

    // Fourmi is a chief, the others members
    private suspend fun AppTestScope.syncMembers() {
        fourmizzz.players("202610201200", listOf(fourmi, abeille, cigale, fourmi.copy(id = 126, pseudo = "Intrus", alliance = "LRM")))
        sync(Exports.Players.Sync(version = "202610201200"))
        signIn("Fourmi")
    }

    private suspend fun AppTestScope.page(resource: Alliance = Alliance()) = plain(client.get(resource).bodyAsText())

    private suspend fun AppTestScope.settle(pseudo: String, settlement: String, filter: String? = "all"): HttpResponse =
        browser.submitForm(
            "/alliance/members/$pseudo/settlement",
            parameters {
                append("settlement", settlement)
                filter?.let { append("filter", it) }
            },
        )

    private suspend fun AppTestScope.storedSettlements() =
        query("SELECT id, wage_settlement FROM players ORDER BY id") { it.getLong(0) to it.getString(1) }

    private fun pseudos(page: String) =
        Regex("""<tr[^>]* data-pseudo="([^"]*)">""").findAll(page).map { it.groupValues[1] }.toList()

    @Test
    fun `a chief syncs Fourmizzz and comes back to the alliance with the members count`() = appTest {
        syncMembers()
        fourmizzz.alliances("202610201300", listOf(empire))
        fourmizzz.players("202610201300", listOf(fourmi, abeille))

        val res = browser.post(Alliance.Sync())

        assertEquals(HttpStatusCode.Found, res.status)
        assertEquals("/alliance?synced=2", res.headers[HttpHeaders.Location])
        val page = page(Alliance(synced = 2))
        assertTrue("✓ 2 membres synchronisés" in page)
        assertTrue("""<h1 class="page-title">Alliance EMP</h1><p class="text-secondary">Empire · <span class="abridged">Fourmizzz 15:00</span><span class="whole">données Fourmizzz d'aujourd'hui 15:00</span></p>""" in page)
        assertTrue("""<span class="caption text-secondary">Joueurs</span><div class="alliance-tile__figure">2</div>""" in page)
        assertTrue("""<span class="abridged">0,00 M cm²</span><span class="whole">1 000 cm²</span>""" in page)
        assertTrue("""<span class="caption text-secondary">Guerres</span><div class="alliance-tile__figure">LRM</div>""" in page)
        assertTrue("""<span class="caption text-secondary">Pactes</span><div class="alliance-tile__figure">ODF</div>""" in page)
        assertEquals(listOf("fourmi", "abeille"), pseudos(page).sorted().reversed())
    }

    @Test
    fun `says why the sync failed`() = appTest {
        syncMembers()
        fourmizzz.alliances("202610201300", listOf(empire))
        fourmizzz.failing = true

        val res = client.post(Alliance.Sync())

        assertEquals(HttpStatusCode.BadGateway, res.status)
        assertTrue("Fourmizzz ne répond pas : rien n'a été synchronisé." in res.bodyAsText())
    }

    @Test
    fun `only chiefs sync`() = appTest {
        syncMembers()
        signIn("abeille", chief = false)

        assertEquals(HttpStatusCode.Forbidden, client.post(Alliance.Sync()).status)
        assertFalse("Synchroniser Fourmizzz" in page())
    }

    @Test
    fun `a member sees the ranks pseudos colonies fields and debts only`() = appTest {
        syncMembers()
        execute("UPDATE players SET debt = 25000, incubator_level = 12 WHERE id = 124")
        signIn("abeille", chief = false)

        val page = page(Alliance(filter = "never-signed-in"))

        assertTrue("""<thead><tr><th scope="col">Rang</th><th scope="col">Joueur</th><th class="number" scope="col">TdC</th><th class="number" scope="col">Dette</th></tr></thead>""" in page)
        assertTrue("""<tr class="emp-table__you" data-pseudo="abeille"><td><span class="emp-badge emp-badge--rank">P5</span></td><td class="alliance-members__member"><span class="alliance-members__pseudo">abeille<span class="emp-badge emp-badge--you">toi</span></span><span class="small text-secondary">colonie de Fourmi</span></td><td class="number amount">60 963 cm²</td><td class="number"><span class="amount debt-amount">25 000</span></td></tr>""" in page)
        assertTrue("""<span class="alliance-members__pseudo">Fourmi<span class="emp-badge emp-badge--leader">chef</span></span>""" in page)
        // No filter: the list is whole
        assertEquals(3, pseudos(page).size)
        assertFalse("Intrus" in page)
        for (hidden in listOf("emp-filter", "Si solde négatif", "Màj niveaux", "Copier le MP", "/alliance/members/", "12 · 0")) {
            assertFalse(hidden in page, hidden)
        }
    }

    @Test
    fun `the filters count right and combine with the pseudo`() = appTest {
        syncMembers()
        execute("UPDATE players SET levels_updated_at = '${clock.now - 2.days}' WHERE id = 124")
        execute("UPDATE players SET levels_updated_at = '${clock.now - 15.days}', wage_settlement = 'ACCUMULATE_DEBT' WHERE id = 125")

        val all = page()
        val stale = page(Alliance(filter = "stale-levels"))
        val combined = page(Alliance(filter = "never-signed-in", pseudo = "CIG"))

        assertTrue("""<a href="/alliance" class="emp-filter" aria-current="true">Tous · 3</a>""" in all)
        assertTrue("""<a href="/alliance?filter=stale-levels" class="emp-filter emp-filter--warning"><span class="abridged">Niveaux &gt; 7 j · 2</span><span class="whole">Niveaux de plus de 7 j · 2</span></a>""" in all)
        assertTrue("""<a href="/alliance?filter=accumulate" class="emp-filter">Cumulent · 1</a>""" in all)
        assertTrue("""<a href="/alliance?filter=never-signed-in" class="emp-filter">Jamais connectés · 2</a>""" in all)
        assertEquals(setOf("fourmi", "cigale"), pseudos(stale).toSet())
        assertTrue("""<span class="alliance-members__stale">il y a 15 j</span>""" in stale)
        assertEquals(listOf("cigale"), pseudos(combined))
        assertTrue("""<a href="/alliance?filter=accumulate&amp;pseudo=CIG" class="emp-filter">Cumulent · 1</a>""" in combined)
        assertTrue("""<input type="hidden" name="filter" value="never-signed-in"><label class="visually-hidden" for="alliance-pseudo">""" in combined)
    }

    @Test
    fun `a chief chooses at once what a member does with a negative balance and can undo it`() = appTest {
        syncMembers()

        val res = settle("abeille", "ACCUMULATE_DEBT", filter = "accumulate")

        assertEquals("/alliance?filter=accumulate&settled=124&previous=DELIVER#members", res.headers[HttpHeaders.Location])
        assertEquals(listOf(123L to "DELIVER", 124L to "ACCUMULATE_DEBT", 125L to "DELIVER"), storedSettlements())
        assertEquals(
            listOf(listOf<Any?>(clock.now.toString(), 123L)),
            query("SELECT wage_settlement_updated_at, wage_settlement_updated_by FROM players WHERE id = 124") { listOf(it.getString(0), it.getLong(1)) },
        )
        val page = page(Alliance(filter = "accumulate", settled = 124, previous = WageSettlement.DELIVER))
        assertTrue("✓ abeille cumule sa dette au prochain calcul" in page)
        assertTrue("""<input type="hidden" name="settlement" value="DELIVER"><button type="submit" class="emp-btn emp-btn--link emp-btn--compact" aria-label="Annuler : abeille envoie">Annuler</button>""" in page)
        assertTrue("""<input type="radio" name="settlement" id="settlement-124-accumulate_debt" value="ACCUMULATE_DEBT" checked="checked">Cumule""" in page)
        assertTrue("""<span class="small text-secondary alliance-settlement__date">à l'instant</span>""" in page)
        assertTrue("« Si solde négatif » s'enregistre aussitôt (avec Annuler) et vaut pour le prochain calcul." in page)

        settle("abeille", "DELIVER")

        assertEquals(listOf(123L to "DELIVER", 124L to "DELIVER", 125L to "DELIVER"), storedSettlements())
    }

    @Test
    fun `keeps the saved choices and levels when members are synced again`() = appTest {
        syncMembers()
        settle("abeille", "ACCUMULATE_DEBT")
        execute("UPDATE players SET incubator_level = 12 WHERE id = 124")

        sync(Exports.Players.Sync(version = "202610201200"))

        assertEquals(listOf(123L to "DELIVER", 124L to "ACCUMULATE_DEBT", 125L to "DELIVER"), storedSettlements())
        assertEquals(listOf(12L), query("SELECT incubator_level FROM players WHERE id = 124") { it.getLong(0) })
    }

    @Test
    fun `an unknown settlement saves nothing and a member cannot choose`() = appTest {
        syncMembers()

        settle("abeille", "PAY_LATER")
        signIn("abeille", chief = false)
        val res = settle("abeille", "ACCUMULATE_DEBT")

        assertEquals(HttpStatusCode.Forbidden, res.status)
        assertEquals(listOf(123L to "DELIVER", 124L to "DELIVER", 125L to "DELIVER"), storedSettlements())
    }

    @Test
    fun `a chief sees the levels their date and the accesses`() = appTest {
        syncMembers()
        execute("UPDATE players SET incubator_level = 12, solarium_level = 15, laying_technique_level = 12, aphid_farm_level = 6, levels_updated_at = '${clock.now - 1.days}' WHERE id = 124")

        val page = page()

        assertTrue("""<td class="alliance-members__levels amount">12 · 15 · 12 · 6</td><td class="number alliance-members__level">12</td>""" in page)
        assertTrue("""<span class="text-secondary">hier</span>""" in page)
        assertTrue("""<span class="text-secondary">à l'instant</span>""" in page)
        assertTrue("""<a href="/alliance/members/abeille">abeille</a>""" in page)
        assertTrue("Niveaux : couveuse · solarium · technique de ponte · étable." in page)
        // On a phone, a line per member leads to their page
        assertTrue("""<a href="/alliance/members/abeille" class="alliance-list__row">""" in page)
        assertTrue("""<span class="small text-secondary">12 · 15 · 12 · 6 · <span>màj hier</span> · <span class="alliance-members__never">jamais connecté</span></span>""" in page)
    }

    @Test
    fun `the chiefs are told which players export the next computation counts`() = appTest {
        syncMembers()

        assertTrue("""✓ Export <span class="whole">joueurs </span>Fourmizzz de 14:00, 3 membres</li>""" in plain(client.get(Wages()).bodyAsText()))
        signIn("abeille", chief = false)
        assertFalse("Fourmizzz de 14:00" in client.get(Wages()).bodyAsText())
    }

    @Test
    fun `the site opens on the viewer's page and the former pages lead to the new ones`() = appTest {
        syncMembers()

        assertEquals("/me", browser.get("/").headers[HttpHeaders.Location])
        for ((former, page) in listOf("/my-deliveries" to "/me", "/update-levels" to "/me", "/debts" to "/alliance", "/access/links" to "/alliance")) {
            val res = browser.get(former)
            assertEquals(HttpStatusCode.MovedPermanently, res.status, former)
            assertEquals(page, res.headers[HttpHeaders.Location], former)
        }
    }
}
