package fr.nathanaudvard.emp

import fr.nathanaudvard.emp.api.resources.Alliances
import fr.nathanaudvard.emp.api.resources.Exports
import fr.nathanaudvard.emp.domain.model.WageDistribution
import fr.nathanaudvard.emp.web.pages.DIGIT_GROUP_SEPARATOR
import fr.nathanaudvard.emp.web.pages.NBSP
import fr.nathanaudvard.emp.web.resources.Me
import fr.nathanaudvard.emp.web.resources.Wages
import io.ktor.client.plugins.resources.get
import io.ktor.client.plugins.resources.post
import io.ktor.client.request.accept
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
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
        )

    @Test
    fun `shows the chiefs when the next computation runs and what needs them`() = appTest {
        computed()
        execute("UPDATE players SET levels_updated_at = '${clock.now}' WHERE id IN (1, 2)")

        val page = page()

        assertTrue("""<section class="emp-card emp-card--accent wages-panel" id="panel" aria-labelledby="panel-title">""" in page)
        assertTrue("""Prochain calcul : aujourd'hui 18:00</h2><span class="emp-badge emp-badge--leader">Chef</span>""" in page)
        assertTrue("""id="computation-time" required="required" value="18:00">""" in page)
        assertTrue("""value="MINIMAL" checked="checked">Minimale</label>""" in page)
        assertTrue("""value="PROPORTIONAL"><span class="abridged">Proport.</span><span class="whole">Proportionnelle</span></label>""" in page)
        assertTrue("""<a href="/wages/compute" class="emp-btn emp-btn--primary">Calculer maintenant…</a>""" in page)
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
        assertFalse("Copier un rappel" in page)
        assertFalse("Copier l'annonce" in page)
        assertTrue("""<div class="wages-tiles"><div class="emp-tile">""" in page)
        assertEquals(HttpStatusCode.Forbidden, client.get(Wages.Compute()).status)
        assertEquals(HttpStatusCode.Forbidden, client.post(Wages.Compute()).status)
        assertEquals(HttpStatusCode.Forbidden, saveSettings("17:00", "PROPORTIONAL").status)
        assertEquals(HttpStatusCode.Forbidden, client.post(Wages.Reminder(computationId = 1, playerId = 1)).status)
        assertEquals(HttpStatusCode.Forbidden, client.post(Wages.Announcement(computationId = 1)).status)
        assertEquals(1L, wageHistory().bounds.latestId)
        assertEquals(WageDistribution.MINIMAL, settings().distribution)
    }

    @Test
    fun `saves the time and distribution of the daily computation`() = appTest {
        computed()

        val res = saveSettings("17:30", "PROPORTIONAL")

        assertEquals(HttpStatusCode.Found, res.status)
        assertEquals("/wages#panel", res.headers[HttpHeaders.Location])
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
    fun `computes now once confirmed and every page shows the new computation`() = appTest {
        computed()

        val confirmation = plain(client.get(Wages.Compute()).bodyAsText())
        assertTrue("Il clôt la période en cours" in confirmation)
        assertTrue("Les envois non faits restent à faire." in confirmation)
        assertTrue("Le calcul de 18:00 partira de ce nouveau calcul" in confirmation)
        assertTrue("""<form action="/wages/compute" method="post" class="wages-compute__actions">""" in confirmation)

        clock.now = NOW + 2.hours
        fourmizzz.players("202610201400", players)
        val res = client.config { followRedirects = false }.post(Wages.Compute())

        assertEquals(HttpStatusCode.Found, res.status)
        assertEquals("/wages", res.headers[HttpHeaders.Location])
        assertEquals(2L, wageHistory().bounds.latestId)
        assertEquals(NOW, latestComputation().since)
        assertTrue("Récolte du mar. 20 oct. 14:30 au mar. 20 oct. 16:30" in page())
        signIn("P1", chief = false)
        assertTrue("Calcul du mar. 20 oct. 16:30" in plain(client.get(Me()).bodyAsText()))
    }

    @Test
    fun `says why nothing was computed`() = appTest {
        computed()

        val res = client.post(Wages.Compute())

        assertEquals(HttpStatusCode.Conflict, res.status)
        assertTrue("Rien n'a été calculé : aucun export joueurs Fourmizzz depuis le calcul précédent." in plain(res.bodyAsText()))
        assertEquals(1L, wageHistory().bounds.latestId)
    }

    @Test
    fun `copies a reminder of what a giver has left to send`() = appTest {
        computed()
        client.submitForm(
            "/alliance/members/P1/convoys",
            parameters { append("message", "Vous allez livrer 0 nourritures et 70000 materiaux à P3 dans 1H") },
        )

        val page = page()
        val reminder = client.post(Wages.Reminder(computationId = 1, playerId = 1)) { accept(ContentType.Text.Plain) }

        assertTrue("""<form action="/wages/1/reminders/1" method="post" class="giver__reminder"><button type="submit" class="emp-btn emp-btn--secondary emp-btn--compact" data-copy="">Copier un rappel</button></form>""" in page)
        assertTrue("/wages/1/reminders/2" in page)
        assertEquals(
            """
            |Salut P1,
            |
            |Il te reste à envoyer pour le calcul du mar. 20 oct. 14:30 :
            |- 12 000 à P4
            |- 8 000 à P3
            |
            |Avant le prochain calcul, mar. 20 oct. 18:00.
            |Le détail, et « J'ai envoyé » une fois fait, sur ta page : http://localhost/me
            """.trimMargin(),
            reminder.bodyAsText(),
        )
    }

    @Test
    fun `copies the deliveries for the forum and nothing else`() = appTest {
        computed()

        val announcement = client.post(Wages.Announcement(computationId = 1)) { accept(ContentType.Text.Plain) }.bodyAsText()
        // Without the script, the page shows the text to copy by hand
        val fallback = plain(client.post(Wages.Announcement(computationId = 1)).bodyAsText())

        assertEquals(
            """
            |Salaires du calcul du mar. 20 oct. 14:30
            |
            |P1 envoie :
            |- 78 000 à P3
            |- 12 000 à P4
            |
            |P2 envoie :
            |- 42 000 à P4
            """.trimMargin(),
            announcement,
        )
        assertTrue("""<label for="copied-text">Annonce pour le forum de l'alliance</label>""" in fallback)
        assertTrue("P2 envoie :\n- 42 000 à P4</textarea>" in fallback)
        assertTrue("""<form action="/wages/1/announcement" method="post" class="wages-panel__announcement">""" in page())
    }
}
