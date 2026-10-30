package fr.nathanaudvard.emp

import fr.nathanaudvard.emp.api.resources.Alliances
import fr.nathanaudvard.emp.api.resources.Exports
import fr.nathanaudvard.emp.domain.model.WageDistribution
import fr.nathanaudvard.emp.web.pages.DIGIT_GROUP_SEPARATOR
import fr.nathanaudvard.emp.web.pages.NBSP
import fr.nathanaudvard.emp.web.resources.Me
import fr.nathanaudvard.emp.web.resources.Wages
import io.ktor.client.plugins.resources.get
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class WagesPanelTest {
    private fun plain(page: String) =
        page.replace(Regex(">\\s+<"), "><").replace(DIGIT_GROUP_SEPARATOR, " ").replace(NBSP, " ")

    private suspend fun AppTestScope.page(resource: Wages = Wages()) = plain(client.get(resource).bodyAsText())

    private val players = mapOf(1L to 4_000L, 2L to 3_000L, 3L to 500L, 4L to 1_000L)
        .map { (id, field) -> fourmi.copy(id = id, pseudo = "P$id", field = field) }

    // P1 and P2 sending to P3 and P4, computed at NOW, P3 signed in as a chief
    private suspend fun AppTestScope.computed(before: suspend () -> Unit = {}) {
        fourmizzz.players("202610201200", players)
        before()
        post(Alliances.Tag.Wages(Alliances.Tag(tag = "EMP"), distribution = "minimal"))
        signIn("P3")
    }

    private suspend fun AppTestScope.saveSettings(time: String, distribution: String) =
        client.config { followRedirects = false }.submitForm(
            "/wages/settings",
            parameters {
                append("time", time)
                append("distribution", distribution)
            },
        ) { htmx() }

    @Test
    fun `shows the chiefs when the next computation runs and what needs them`() = appTest {
        computed()
        execute("UPDATE players SET levels_updated_at = '${clock.now}' WHERE id IN (1, 2)")

        val page = page()

        assertTrue("""<section class="emp-card emp-card--accent wages-panel" id="panel" aria-labelledby="panel-title">""" in page)
        assertTrue("""Prochain calcul : aujourd'hui 18:00</h2><span class="emp-badge emp-badge--leader">Chef</span>""" in page)
        assertFalse("computation-time" in page)
        assertTrue("""value="MINIMAL" checked="checked">Minimale</label>""" in page)
        assertTrue("""value="PROPORTIONAL"><span class="abridged">Proport.</span><span class="whole">Proportionnelle</span></label>""" in page)
        assertTrue("""✓ Export <span class="whole">joueurs </span>Fourmizzz de 14:00, 4 membres</li>""" in page)
        assertTrue("""<a href="/alliance?filter=stale-levels" class="wages-panel__alert--warning"><span class="abridged">2 membres : niveaux de plus de 7 j ›</span><span class="whole">2 membres n'ont pas touché leurs niveaux depuis 7 j ›</span></a>""" in page)
        assertFalse("de dette</span> ›" in page)
        assertTrue("""<div class="wages-tiles wages-tiles--chief"><div class="emp-tile">""" in page)
        assertTrue("""<div class="emp-tile wages-tile--to-send">""" in page)
    }

    @Test
    fun `points out who accumulates in the latest computation`() = appTest {
        computed {
            sync(Exports.Players.Sync())
            execute("UPDATE players SET wage_settlement = 'ACCUMULATE_DEBT' WHERE id = 2")
        }

        assertTrue("""<li><a href="/alliance?filter=accumulate">P2 cumule aujourd'hui<span class="whole"> : +42 000 de dette</span> ›</a></li>""" in page())
    }

    @Test
    fun `keeps the panel and its actions to the chiefs`() = appTest {
        computed()
        signIn("P1", chief = false)

        val page = page()

        assertFalse("wages-panel" in page)
        assertTrue("""<div class="wages-tiles"><div class="emp-tile">""" in page)
        assertEquals(HttpStatusCode.Forbidden, saveSettings("17:00", "PROPORTIONAL").status)
        assertEquals(WageDistribution.MINIMAL, settings().distribution)
    }

    @Test
    fun `saves the time and distribution of the daily computation`() = appTest {
        computed()

        val res = saveSettings("17:30", "PROPORTIONAL")

        assertEquals(HttpStatusCode.OK, res.status)
        val page = page()
        assertTrue("Prochain calcul : aujourd'hui 17:30</h2>" in page)
        assertTrue("""value="PROPORTIONAL" checked="checked">""" in page)
        assertTrue("Réglé à l'instant par P3" in page)

        // An impossible time changes nothing
        saveSettings("25:00", "MINIMAL")
        assertTrue("Prochain calcul : aujourd'hui 17:30</h2>" in page())

        // Each member's page counts down to it
        signIn("P1", chief = false)
        assertTrue("<span>· prochain aujourd'hui 17:30</span>" in plain(client.get(Me()).bodyAsText()))
        clock.now = Instant.parse("2026-10-20T16:00:00Z")
        val me = plain(client.get(Me()).bodyAsText())
        assertTrue("<span>· prochain demain 17:30</span>" in me)
        assertTrue("""<h2 class="me-card-caption" id="send-title">À envoyer avant demain 17:30</h2>""" in me)
    }

    @Test
    fun `keeps the time saved when the panel sends the distribution alone`() = appTest {
        computed()
        saveSettings("17:30", "MINIMAL")

        val res = client.submitForm("/wages/settings", parameters { append("distribution", "PROPORTIONAL") }) { htmx() }

        assertEquals(HttpStatusCode.OK, res.status)
        assertEquals(LocalTime(17, 30), settings().computationTime)
        assertEquals(WageDistribution.PROPORTIONAL, settings().distribution)
    }

    @Test
    fun `htmx gets the panel alone once the settings are saved`() = appTest {
        computed()

        val res = client.config { followRedirects = false }.submitForm(
            "/wages/settings",
            parameters {
                append("time", "17:30")
                append("distribution", "PROPORTIONAL")
            },
        ) { htmx() }

        assertEquals(HttpStatusCode.OK, res.status)
        val panel = plain(res.bodyAsText())
        assertEquals(listOf("panel"), sectionIds(panel))
        assertTrue("Prochain calcul : aujourd'hui 17:30</h2>" in panel)
    }

    // The computation is started by the API only: the panel has no button for it
    @Test
    fun `the panel neither computes nor syncs`() = appTest {
        computed()

        val page = page()

        assertFalse("Calculer" in page)
        assertFalse("Synchroniser" in page)
        assertFalse("Copier" in page)
    }
}
