package fr.nathanaudvard.emp

import fr.nathanaudvard.emp.api.resources.Alliances
import fr.nathanaudvard.emp.web.pages.DIGIT_GROUP_SEPARATOR
import fr.nathanaudvard.emp.web.pages.NBSP
import fr.nathanaudvard.emp.web.resources.Alliance
import io.ktor.client.plugins.resources.get
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MemberPageTest {
    // Without the indentation between tags, with plain spaces in the amounts and dates
    private fun plain(page: String) =
        page.replace(Regex(">\\s+<"), "><").replace(DIGIT_GROUP_SEPARATOR, " ").replace(NBSP, " ")

    private val AppTestScope.browser get() = client.config { followRedirects = false }

    // P1 and P2 send to P3 and P4. P3, a chief, is signed in
    private suspend fun AppTestScope.computation() {
        val fields = mapOf(1L to 4_000L, 2L to 3_000L, 3L to 500L, 4L to 1_000L)
        fourmizzz.players("202610201200", fields.map { (id, field) -> fourmi.copy(id = id, pseudo = "P$id", field = field) })
        post(Alliances.Tag.Wages(Alliances.Tag(tag = "EMP"), distribution = "minimal"))
        signIn("P3")
    }

    private suspend fun AppTestScope.page(pseudo: String) = plain(client.get(Alliance.Member(pseudo = pseudo)).bodyAsText())

    private suspend fun AppTestScope.act(path: String, vararg values: Pair<String, String>): HttpResponse =
        browser.submitForm(path, parameters { values.forEach { (name, value) -> append(name, value) } }) { htmx() }

    @Test
    fun `a chief marks a delivery sent in the member's name and keeps their own name on it`() = appTest {
        computation()

        val res = act("/alliance/members/P1/computations/1/deliveries/3/sent")

        assertEquals(HttpStatusCode.OK, res.status)
        assertEquals(
            listOf(listOf<Any?>(1L, 3L, 78_000L, "declared", 3L)),
            query("SELECT from_player_id, to_player_id, amount, source, declared_by FROM convoys") { c -> listOf(c.getLong(0), c.getLong(1), c.getLong(2), c.getString(3), c.getLong(4)) },
        )
        val page = page("P1")
        assertTrue("""<h2 class="card-title" id="send-title">Envoie 12 000</h2><span class="small text-secondary">calcul du 20 oct.</span>""" in page)
        assertTrue("""aria-label="Marquer envoyé à P4">Marquer envoyé</button>""" in page)
        assertTrue("déclaré par P3 " in page)
        assertTrue("Un convoi saisi par un chef garde le nom du chef dans l'historique." in page)
    }

    @Test
    fun `a chief pastes a message of the game in the member's name`() = appTest {
        computation()

        val res = act("/alliance/members/P1/convoys", "message" to "Vous allez livrer 0 nourritures et 12 000 materiaux à P4 dans 1H")

        assertEquals(HttpStatusCode.OK, res.status)
        assertTrue("1 convoi enregistré" in plain(res.bodyAsText()))
        assertEquals(listOf(listOf(1L, 4L, 3L)), query("SELECT from_player_id, to_player_id, declared_by FROM convoys") { c -> (0..2).map { c.getLong(it) } })
        assertTrue("collé par P3 " in page("P1"))
    }

    @Test
    fun `a chief saves the levels of a member who is named their author`() = appTest {
        computation()

        val res = act("/alliance/members/P1/levels/INCUBATOR", "level" to "3")

        assertEquals(listOf(listOf(3L, 3L)), query("SELECT incubator_level, levels_updated_by FROM players WHERE id = 1") { c -> (0..1).map { c.getLong(it) } })
        val levels = plain(res.bodyAsText())
        assertTrue("""<h2 class="card-title" id="levels-title">Niveaux<span class="emp-badge emp-badge--leader">Chef</span></h2><span class="small text-secondary me-levels__date">mis à jour par P3 à l'instant</span>""" in levels)
        assertTrue("✓ Couveuse 0 → 3 enregistré" in levels)
        assertTrue("Visible par le membre et les chefs seulement." in levels)
        val page = page("P1")
        assertTrue("""<summary class="emp-disclosure emp-disclosure--card">Détail de son calcul""" in page)
        assertTrue("""<th scope="row">Production propre</th><td class="number amount">192 000</td>""" in page)
    }

    @Test
    fun `tells the member's field colony debt and settlement`() = appTest {
        computation()
        act("/alliance/members/P3/settlement", "settlement" to "ACCUMULATE_DEBT")

        val page = page("p3")

        assertTrue("""<p class="text-secondary">TdC 500 cm² · pas de colonie · aucune dette</p>""" in page)
        assertFalse("✓ Il cumule en dette au prochain calcul" in page)
        assertTrue("Cumule sans effet : reçoit" in page)
        assertTrue("Choisi par P3 à l'instant" in page)
        assertTrue("""<input type="radio" name="settlement" id="settlement-3-accumulate_debt" value="ACCUMULATE_DEBT" checked="checked">Il cumule en dette""" in page)
    }

    @Test
    fun `the settlement chosen on the page can be undone`() = appTest {
        computation()

        val res = act("/alliance/members/P1/settlement", "settlement" to "ACCUMULATE_DEBT")

        val page = plain(res.bodyAsText())
        assertTrue("✓ Il cumule en dette au prochain calcul" in page)
        assertTrue("""<input type="hidden" name="settlement" value="DELIVER"><button type="submit" class="emp-btn emp-btn--link emp-btn--compact" aria-label="Annuler : il envoie">Annuler</button>""" in page)
    }

    @Test
    fun `htmx gets the sections a chief's action changed`() = appTest {
        computation()

        val levels = browser.submitForm("/alliance/members/P1/levels/INCUBATOR", parameters { append("level", "3") }) { htmx() }
        val settled = browser.submitForm("/alliance/members/P1/settlement", parameters { append("settlement", "ACCUMULATE_DEBT") }) { htmx() }
        val declared = browser.submitForm("/alliance/members/P1/computations/1/deliveries/3/sent", parameters { }) { htmx() }
        val named = browser.submitForm("/alliance/members/P1/chief", parameters { append("chief", "true") }) { htmx() }

        assertEquals(listOf("levels"), sectionIds(levels.bodyAsText()))
        assertTrue("✓ Couveuse 0 → 3 enregistré" in plain(levels.bodyAsText()))
        assertEquals(listOf("settlement"), sectionIds(settled.bodyAsText()))
        assertTrue("✓ Il cumule en dette au prochain calcul" in plain(settled.bodyAsText()))
        assertEquals(listOf("sending"), sectionIds(declared.bodyAsText()))
        assertTrue("déclaré par P3 " in plain(declared.bodyAsText()))
        assertEquals(listOf("access", "member-header"), sectionIds(named.bodyAsText()))
        assertTrue("✓ P1 est chef de l'alliance." in plain(named.bodyAsText()))
    }

    @Test
    fun `htmx loads the list when a chief gives up their own role`() = appTest {
        computation()
        act("/alliance/members/P1/chief", "chief" to "true")

        val res = browser.submitForm("/alliance/members/P3/chief", parameters { }) { htmx() }

        assertEquals(HttpStatusCode.NoContent, res.status)
        assertEquals("/alliance", res.headers["HX-Redirect"])
    }

    @Test
    fun `every action of the page is refused to a member`() = appTest {
        computation()
        signIn("P1", chief = false)

        assertEquals(HttpStatusCode.Forbidden, client.get(Alliance.Member(pseudo = "P2")).status)
        assertEquals(HttpStatusCode.Forbidden, client.get(Alliance.Member(pseudo = "P1")).status)
        for (path in listOf(
            "levels/INCUBATOR", "levels/confirm", "levels/changes/1/undo", "computations/1/deliveries/3/sent", "convoys",
            "convoys/delete", "settlement", "chief", "message", "replace",
        )) {
            assertEquals(HttpStatusCode.Forbidden, act("/alliance/members/P1/$path", "level" to "5", "chief" to "true").status, path)
        }
        assertEquals(listOf(0L), query("SELECT count(*) FROM convoys") { it.getLong(0) })
        assertEquals(listOf(3L), query("SELECT id FROM players WHERE is_chief = 1") { it.getLong(0) })
    }

    @Test
    fun `the alliance always keeps a chief`() = appTest {
        computation()

        val alone = plain(act("/alliance/members/P3/chief").bodyAsText())
        val named = plain(act("/alliance/members/P1/chief", "chief" to "true").bodyAsText())
        val left = act("/alliance/members/P3/chief")

        assertTrue("P3 est le dernier chef : nomme d'abord un autre chef." in alone)
        assertTrue("✓ P1 est chef de l'alliance." in named)
        assertEquals("/alliance", left.headers["HX-Redirect"])
        assertEquals(listOf(1L), query("SELECT id FROM players WHERE is_chief = 1") { it.getLong(0) })
    }

    @Test
    fun `says when no member has the pseudo`() = appTest {
        computation()

        val res = client.get(Alliance.Member(pseudo = "Inconnu"))

        assertEquals(HttpStatusCode.OK, res.status)
        assertTrue("Aucun membre de l'alliance n'a ce pseudo." in res.bodyAsText())
        assertEquals("/alliance", act("/alliance/members/Inconnu/settlement", "settlement" to "DELIVER").headers["HX-Redirect"])
    }
}
