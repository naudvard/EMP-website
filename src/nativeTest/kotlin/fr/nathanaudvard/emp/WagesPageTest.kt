package fr.nathanaudvard.emp

import fr.nathanaudvard.emp.api.resources.Alliances
import fr.nathanaudvard.emp.api.resources.Exports
import fr.nathanaudvard.emp.web.resources.Wages
import io.ktor.client.plugins.resources.get
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

class WagesPageTest {
    // Giver, recipient and amount of each delivery, read from the giver cards
    private fun deliveryRows(page: String) =
        Regex("""<article class="giver">([\s\S]*?)</article>""").findAll(page).flatMap { card ->
            val giver = Regex("""<span class="pseudo">([^<]*)</span>""").find(card.value)!!.groupValues[1]
            Regex("""<li>\s*<span class="pseudo">([^<]*)</span>\s*<span class="amount">([^<]*)</span>""")
                .findAll(card.value).map { listOf(giver) + it.destructured.toList() }
        }.toList()

    private fun giverTotals(page: String) =
        Regex("""<span class="amount give">([^<]*)</span>""").findAll(page).map { it.groupValues[1] }.toList()

    // Pseudo, debt (empty when none) and balance of each row of the summary
    private fun balanceRows(page: String) =
        Regex("""<tr>\s*<td>([^<]*?)\s*(?:<span class="debt">dette ([^<]*)</span>\s*)?</td>[\s\S]*?<td class="amount [a-z]+">([^<]*)</td>""")
            .findAll(page).map { it.destructured.toList() }.toList()

    @Test
    fun `says when no wages have been computed yet`() = appTest {
        val res = client.get(Wages())

        assertEquals(HttpStatusCode.OK, res.status)
        assertTrue("Aucun calcul de salaires pour l'instant." in res.bodyAsText())
    }

    @Test
    fun `shows who gives what to whom in the latest computation`() = appTest {
        val fields = mapOf(1L to 4_000L, 2L to 3_000L, 3L to 500L, 4L to 1_000L)
        val players = fields.map { (id, field) -> fourmi.copy(id = id, pseudo = "P$id", field = field) }
        clock.now = NOW - 24.hours
        fourmizzz.players("202610191200", players)
        post(Alliances.Tag.Wages(Alliances.Tag(tag = "EMP"), distribution = "proportional"))
        clock.now = NOW
        fourmizzz.players("202610201200", players)
        post(Alliances.Tag.Wages(Alliances.Tag(tag = "EMP"), distribution = "minimal"))

        val page = client.get(Wages()).bodyAsText()

        assertTrue("Calculé le 20/10/2026 à 14:30 depuis le 19/10/2026 à 14:30 · 1 h de mesures · répartition minimale" in page)
        assertEquals(
            listOf(
                listOf("P1", "P3", "78 000"),
                listOf("P1", "P4", "12 000"),
                listOf("P2", "P4", "42 000"),
            ),
            deliveryRows(page),
        )
        assertEquals(listOf("90 000 bois", "42 000 bois"), giverTotals(page))
        assertEquals(
            listOf(
                listOf("P1", "", "−90 000"),
                listOf("P2", "", "−42 000"),
                listOf("P3", "", "+78 000"),
                listOf("P4", "", "+54 000"),
            ),
            balanceRows(page),
        )
    }

    @Test
    fun `shows the debt each player has accumulated once the computation is settled`() = appTest {
        val fields = mapOf(1L to 4_000L, 2L to 3_000L, 3L to 500L, 4L to 1_000L)
        val players = fields.map { (id, field) -> fourmi.copy(id = id, pseudo = "P$id", field = field) }
        clock.now = NOW - 24.hours
        fourmizzz.players("202610191200", players)
        sync(Exports.Players.Sync())
        execute("UPDATE players SET wage_settlement = 'ACCUMULATE_DEBT' WHERE id = 2")
        post(Alliances.Tag.Wages(Alliances.Tag(tag = "EMP"), distribution = "minimal"))
        clock.now = NOW
        fourmizzz.players("202610201200", players)
        post(Alliances.Tag.Wages(Alliances.Tag(tag = "EMP"), distribution = "minimal"))

        val latest = client.get(Wages()).bodyAsText()
        val first = client.get(Wages(id = 1)).bodyAsText()

        // The debt is shown in the summary: there is no tab of its own
        assertFalse("tab-dettes" in latest)
        assertEquals(listOf("", "84 000", "", ""), balanceRows(latest).map { it[1] })
        assertEquals(listOf("", "42 000", "", ""), balanceRows(first).map { it[1] })
    }

    @Test
    fun `steps between past computations`() = appTest {
        for (hoursAgo in 2 downTo 0) {
            clock.now = NOW - hoursAgo.hours
            fourmizzz.players("20261020${12 - hoursAgo}00", listOf(fourmi.copy(id = 1, pseudo = "P1"), fourmi.copy(id = 2, pseudo = "P2")))
            post(Alliances.Tag.Wages(Alliances.Tag(tag = "EMP"), distribution = "proportional"))
        }

        val latest = client.get(Wages()).bodyAsText()
        assertTrue("""<a href="/wages?id=2" class="step">← Plus ancien</a>""" in latest)
        assertTrue("""<span class="step" aria-disabled="true">Plus récent →</span>""" in latest)
        assertTrue("""value="2026-10-20" min="2026-10-20" max="2026-10-20"""" in latest)
        assertFalse("Ancien calcul." in latest)

        val res = client.get(Wages(id = 2))
        val middle = res.bodyAsText()
        assertEquals(HttpStatusCode.OK, res.status)
        assertTrue("Ancien calcul." in middle)
        assertTrue("répartition proportionnelle</p>" in middle)
        assertTrue("""<a href="/wages?id=1" class="step">← Plus ancien</a>""" in middle)
        assertTrue("""<a href="/wages?id=3" class="step">Plus récent →</a>""" in middle)

        val oldest = client.get(Wages(id = 1)).bodyAsText()
        assertTrue("""<span class="step" aria-disabled="true">← Plus ancien</span>""" in oldest)
    }

    @Test
    fun `shows the latest computation of a date`() = appTest {
        clock.now = NOW - 1.hours
        fourmizzz.players("202610201100", listOf(fourmi.copy(id = 1, pseudo = "P1")))
        post(Alliances.Tag.Wages(Alliances.Tag(tag = "EMP"), distribution = "proportional"))
        clock.now = NOW
        fourmizzz.players("202610201200", listOf(fourmi.copy(id = 1, pseudo = "P1")))
        post(Alliances.Tag.Wages(Alliances.Tag(tag = "EMP"), distribution = "minimal"))

        // Computed on 20/10 at 14:30 game time: the latest one of that day or a later one
        for (date in listOf("2026-10-20", "2026-10-25")) {
            assertTrue("répartition minimale</p>" in client.get(Wages(date = date)).bodyAsText(), date)
        }
        for (date in listOf("2026-10-19", "20/10/2026")) {
            val page = client.get(Wages(date = date)).bodyAsText()
            assertTrue("Aucun calcul à cette date ou avant." in page, date)
            assertTrue("""<a href="/wages">Voir le dernier calcul</a>""" in page, date)
        }
    }

    @Test
    fun `says when the computation does not exist`() = appTest {
        fourmizzz.players("202610201200", listOf(fourmi.copy(id = 1, pseudo = "P1")))
        post(Alliances.Tag.Wages(Alliances.Tag(tag = "EMP")))

        val res = client.get(Wages(id = 42))

        assertEquals(HttpStatusCode.OK, res.status)
        val page = res.bodyAsText()
        assertTrue("Ce calcul n'existe pas." in page)
        assertTrue("""<a href="/wages">Voir le dernier calcul</a>""" in page)
    }

    @Test
    fun `navigation links to the pages`() = appTest {
        val page = client.get(Wages()).bodyAsText()

        assertTrue("""<a href="/wages" aria-current="page">Salaires</a>""" in page)
        assertTrue("""<a href="/update-levels">Mes niveaux</a>""" in page)
        assertTrue("""<a href="/debts">Dettes</a>""" in page)
    }

    @Test
    fun `shows what the convoys recorded cover`() = appTest {
        val fields = mapOf(1L to 4_000L, 2L to 3_000L, 3L to 500L, 4L to 1_000L)
        val players = fields.map { (id, field) -> fourmi.copy(id = id, pseudo = "P$id", field = field) }
        clock.now = NOW - 24.hours
        fourmizzz.players("202610191200", players)
        post(Alliances.Tag.Wages(Alliances.Tag(tag = "EMP"), distribution = "minimal"))
        clock.now = NOW
        fourmizzz.players("202610201200", players)
        post(Alliances.Tag.Wages(Alliances.Tag(tag = "EMP"), distribution = "minimal"))
        for (message in listOf("et 78 000 materiaux à P3 dans", "et 2 000 materiaux à P4 dans")) {
            client.submitForm("/my-deliveries?pseudo=P1", parameters { append("message", "Vous allez livrer 0 nourritures $message 1H") })
        }

        val page = client.get(Wages()).bodyAsText()

        // Drawn behind the rows, without a line of its own
        assertTrue(Regex("""<div class="giver-head partial" title="80 000 livrés, reste 10 000" style="--sent: 88%">""") in page)
        assertTrue(Regex("""<li class="done" title="✓ livré">\s*<span class="pseudo">P3</span>\s*<span class="amount">78 000</span>""") in page)
        assertTrue(Regex("""<li class="partial" title="2 000 livrés, reste 10 000" style="--sent: 16%">\s*<span class="pseudo">P4</span>""") in page)
        assertFalse("class=\"progress" in page)
    }
}
