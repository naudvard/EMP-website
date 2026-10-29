package fr.nathanaudvard.emp

import com.fleeksoft.ksoup.Ksoup
import fr.nathanaudvard.emp.api.resources.Alliances
import fr.nathanaudvard.emp.api.resources.Exports
import fr.nathanaudvard.emp.domain.model.Viewer
import fr.nathanaudvard.emp.web.pages.DIGIT_GROUP_SEPARATOR
import fr.nathanaudvard.emp.web.pages.NBSP
import fr.nathanaudvard.emp.web.pages.wagesPage
import fr.nathanaudvard.emp.web.resources.Wages
import io.ktor.client.plugins.resources.get
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import kotlinx.html.html
import kotlinx.html.stream.createHTML
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

class WagesPageTest {
    // Without the indentation between tags, with plain spaces in the amounts and dates
    private fun plain(page: String) =
        page.replace(Regex(">\\s+<"), "><").replace(DIGIT_GROUP_SEPARATOR, " ").replace(NBSP, " ")

    private suspend fun AppTestScope.page(resource: Wages = Wages()) = plain(client.get(resource).bodyAsText())

    // Giver, recipient and amount of each delivery still to send, read from the giver cards
    private fun deliveryRows(page: String) =
        Ksoup.parse(page).select("article.giver").flatMap { card ->
            val giver = card.selectFirst(".giver__pseudo")!!.ownText()
            card.select(".giver__delivery").map { delivery ->
                listOf(
                    giver,
                    delivery.selectFirst(".giver__recipient")!!.ownText().removePrefix("→ "),
                    delivery.selectFirst("> .amount")!!.text(),
                )
            }
        }

    private fun leftToSend(page: String) = Ksoup.parse(page).select(".giver__left > .amount").map { it.text() }

    private fun giverPseudos(page: String) = Ksoup.parse(page).select(".giver__pseudo").map { it.ownText() }

    // Pseudo, whether they accumulate, balance and debt of each row of the balances
    private fun balanceRows(page: String) =
        Ksoup.parse(page).select("tr[data-pseudo]").map { row ->
            val cells = row.select("> td").toList()
            listOf(
                cells.first().ownText().removeSuffix(" (toi)"),
                if (cells.first().selectFirst(".badge-accumulates") != null) "cumule" else "",
                cells[cells.size - 2].text(),
                cells.last().text(),
            )
        }

    private fun tiles(page: String) =
        Ksoup.parse(page).select(".emp-tile:has(> .wages-tile__figure)").map { tile ->
            tile.selectFirst("> .caption")!!.text() to tile.selectFirst("> .wages-tile__figure")!!.text()
        }

    // Four players, P1 and P2 sending to P3 and P4, the second computation a day after the first
    private suspend fun AppTestScope.twoComputations(before: suspend () -> Unit = {}) {
        val fields = mapOf(1L to 4_000L, 2L to 3_000L, 3L to 500L, 4L to 1_000L)
        val players = fields.map { (id, field) -> fourmi.copy(id = id, pseudo = "P$id", field = field) }
        clock.now = NOW - 24.hours
        fourmizzz.players("202610191200", players)
        before()
        post(Alliances.Tag.Wages(Alliances.Tag(tag = "EMP"), distribution = "proportional"))
        clock.now = NOW
        fourmizzz.players("202610201200", players)
        post(Alliances.Tag.Wages(Alliances.Tag(tag = "EMP"), distribution = "minimal"))
        signIn("P3")
    }

    private suspend fun AppTestScope.recordConvoys(giver: String, vararg convoys: Pair<String, String>) {
        for ((recipient, amount) in convoys) {
            client.submitForm(
                "/alliance/members/$giver/convoys",
                parameters { append("message", "Vous allez livrer 0 nourritures et $amount materiaux à $recipient dans 1H") },
            )
        }
    }

    @Test
    fun `says when no wages have been computed yet`() = appTest {
        syncAndSignIn()
        val res = client.get(Wages())

        assertEquals(HttpStatusCode.OK, res.status)
        assertTrue("Aucun calcul de salaires pour l'instant." in res.bodyAsText())
    }

    @Test
    fun `shows the period the figures and who sends what to whom in the latest computation`() = appTest {
        twoComputations()

        val page = page()

        assertTrue("<p class=\"wages-header__period text-secondary\">Récolte du lun. 19 oct. 14:30 au mar. 20 oct. 14:30 · 1 mesure horaire · répartition minimale</p>" in page)
        assertEquals(
            listOf("Récolte de l'alliance" to "408 000", "À envoyer · 3 envois" to "132 000", "Envoyé" to "0 %", "Reste · 2 livreurs" to "132 000"),
            tiles(page),
        )
        // The largest amount left first
        assertEquals(
            listOf(
                listOf("P1", "P3", "78 000"),
                listOf("P1", "P4", "12 000"),
                listOf("P2", "P4", "42 000"),
            ),
            deliveryRows(page),
        )
        assertEquals(listOf("90 000", "42 000"), leftToSend(page))
        assertTrue("le plus gros reste d'abord" in page)
        assertEquals(
            listOf(
                listOf("P1", "", "−90 000", "—"),
                listOf("P2", "", "−42 000", "—"),
                listOf("P3", "", "+78 000", "—"),
                listOf("P4", "", "+54 000", "—"),
            ),
            balanceRows(page),
        )
        assertTrue("""<td class="number balance--positive">+78 000</td>""" in page)
        assertTrue("""<td class="number">−90 000</td>""" in page)
        assertFalse("balance-bar" in page)
    }

    @Test
    fun `shows the players who accumulate and the debt they carry once the computation is settled`() = appTest {
        twoComputations {
            sync(Exports.Players.Sync())
            execute("UPDATE players SET wage_settlement = 'ACCUMULATE_DEBT' WHERE id = 2")
        }

        val latest = page()
        val first = page(Wages(id = 1))

        assertEquals(listOf("", "cumule", "", ""), balanceRows(latest).map { it[1] })
        assertEquals(listOf("—", "84 000", "—", "—"), balanceRows(latest).map { it[3] })
        assertEquals(listOf("—", "42 000", "—", "—"), balanceRows(first).map { it[3] })
        assertTrue("""<td class="number debt-amount"><span>84 000</span></td>""" in latest)
    }

    @Test
    fun `abridges large amounts on a phone and keeps them whole on a desktop`() = appTest {
        val players = listOf(fourmi.copy(id = 1, pseudo = "P1", field = 300_000), fourmi.copy(id = 2, pseudo = "P2", field = 1_000))
        fourmizzz.players("202610201200", players)
        post(Alliances.Tag.Wages(Alliances.Tag(tag = "EMP")))
        signIn("P1")

        val page = page()

        assertTrue("""<span class="wages-tile__figure"><span class="abridged">14,4 M</span><span class="whole">14 448 000</span></span>""" in page)
    }

    @Test
    fun `steps between past computations`() = appTest {
        for (hoursAgo in 2 downTo 0) {
            clock.now = NOW - hoursAgo.hours
            fourmizzz.players("20261020${12 - hoursAgo}00", listOf(fourmi.copy(id = 1, pseudo = "P1"), fourmi.copy(id = 2, pseudo = "P2")))
            post(Alliances.Tag.Wages(Alliances.Tag(tag = "EMP"), distribution = "proportional"))
        }
        signIn("P1")

        val latest = page()
        assertTrue("""<a href="/wages?id=2" class="wages-picker__step" aria-label="Calcul précédent">""" in latest)
        assertTrue("""<span class="wages-picker__step" aria-disabled="true" aria-label="Pas de calcul plus récent">""" in latest)
        assertTrue("""<summary class="wages-date__button">mar. 20 oct. · 14:30<span class="emp-badge emp-badge--neutral">dernier</span></summary>""" in latest)
        assertTrue("""value="2026-10-20" min="2026-10-20" max="2026-10-20"""" in latest)
        assertFalse("Ancien calcul" in latest)

        val res = client.get(Wages(id = 2))
        val middle = plain(res.bodyAsText())
        assertEquals(HttpStatusCode.OK, res.status)
        assertTrue("""Ancien calcul ·<a href="/wages">voir le dernier</a>""" in middle)
        assertFalse(">dernier</span>" in middle)
        assertTrue("répartition proportionnelle</p>" in middle)
        assertTrue("""<a href="/wages?id=1" class="wages-picker__step" aria-label="Calcul précédent">""" in middle)
        assertTrue("""<a href="/wages?id=3" class="wages-picker__step" aria-label="Calcul suivant">""" in middle)

        val oldest = page(Wages(id = 1))
        assertTrue("""<span class="wages-picker__step" aria-disabled="true" aria-label="Pas de calcul plus ancien">""" in oldest)
    }

    @Test
    fun `shows the latest computation of a date`() = appTest {
        clock.now = NOW - 1.hours
        fourmizzz.players("202610201100", listOf(fourmi.copy(id = 1, pseudo = "P1")))
        post(Alliances.Tag.Wages(Alliances.Tag(tag = "EMP"), distribution = "proportional"))
        clock.now = NOW
        fourmizzz.players("202610201200", listOf(fourmi.copy(id = 1, pseudo = "P1")))
        post(Alliances.Tag.Wages(Alliances.Tag(tag = "EMP"), distribution = "minimal"))
        signIn("P1")

        // Computed on 20/10 at 14:30 game time: the latest one of that day or a later one
        for (date in listOf("2026-10-20", "2026-10-25")) {
            assertTrue("répartition minimale</p>" in page(Wages(date = date)), date)
        }
        for (date in listOf("2026-10-19", "20/10/2026")) {
            val page = page(Wages(date = date))
            assertTrue("Aucun calcul à cette date ou avant." in page, date)
            assertTrue("""<a href="/wages">Voir le dernier calcul</a>""" in page, date)
        }
    }

    @Test
    fun `says when the computation does not exist`() = appTest {
        fourmizzz.players("202610201200", listOf(fourmi.copy(id = 1, pseudo = "P1")))
        post(Alliances.Tag.Wages(Alliances.Tag(tag = "EMP")))
        signIn("P1")

        val res = client.get(Wages(id = 42))

        assertEquals(HttpStatusCode.OK, res.status)
        val page = res.bodyAsText()
        assertTrue("Ce calcul n'existe pas." in page)
        assertTrue("""<a href="/wages">Voir le dernier calcul</a>""" in page)
    }

    @Test
    fun `the banner links to the pages`() = appTest {
        syncAndSignIn()
        val page = page()

        val logo = Ksoup.parse(page).selectFirst("header.emp-header > .emp-header__inner > a.emp-logo:first-child")!!
        assertEquals("/me", logo.attr("href"))
        assertEquals(listOf("svg"), logo.children().map { it.tagName() })
        assertEquals("EMP", logo.text())
        assertTrue("""<nav class="emp-nav" aria-label="Navigation principale"><a href="/me" class="emp-tab">Moi</a><a href="/wages" class="emp-tab" aria-current="page">Salaires</a><a href="/alliance" class="emp-tab">Alliance</a></nav><span class="emp-header__user">Fourmi · chef</span>""" in page)
        // Migrated to the design system: without the former styles
        assertFalse("<style>" in page)
    }

    @Test
    fun `shows what the convoys recorded cover and folds the givers who sent everything`() = appTest {
        twoComputations()
        recordConvoys("P1", "P3" to "78 000", "P4" to "2 000")

        val page = page()
        
        assertEquals(listOf("P1", "P2"), giverPseudos(page).reversed())
        assertEquals(listOf("42 000", "10 000"), leftToSend(page))
        assertTrue("""role="progressbar" aria-label="Envoyé par P1" aria-valuemin="0" aria-valuemax="100" aria-valuenow="88"""" in page)
        assertTrue("""<li class="giver__delivery giver__delivery--done" data-pseudo="p3"><span class="giver__recipient">→ P3</span><span class="amount">78 000</span><span class="giver__state"><span class="emp-row__done">✓<span class="giver__state-word"> envoyé</span></span></span></li>""" in page)
        assertTrue("""<span class="giver__recipient">→ P4<span class="giver__partial caption text-secondary">2 000 envoyés, reste 10 000</span></span><span class="amount">12 000</span><span class="giver__state"><span class="giver__state-left small text-secondary">reste 10 000</span></span>""" in page)
        assertEquals(listOf("Envoyé" to "60 %", "Reste · 2 livreurs" to "52 000"), tiles(page).drop(2))

        recordConvoys("P1", "P4" to "10 000")
        val folded = page()

        assertEquals(listOf("42 000"), leftToSend(folded))
        assertTrue("""<article class="emp-card giver giver--done" data-pseudo="p1"><span class="player-name giver__pseudo">P1</span><span class="amount">90 000</span><span class="emp-row__done">✓ tout envoyé</span></article>""" in folded)
        assertTrue(folded.indexOf("""data-pseudo="p2"""") < folded.indexOf("""data-pseudo="p1""""))
        assertEquals(listOf("Envoyé" to "68 %", "Reste · 1 livreur" to "42 000"), tiles(folded).drop(2))
    }

    @Test
    fun `explains the method with the constants of the calculation and the totals of the computation`() = appTest {
        twoComputations()

        val page = page()

        assertTrue("""<details class="method" id="method"><summary class="emp-disclosure emp-disclosure--card">Comment sont calculés les salaires ?<svg class="emp-disclosure__chevron"""" in page)
        assertTrue("48 bois par cm² de TdC et par jour" in page)
        assertTrue("Une colonie reverse à son maître 20 % de sa production, plus 1 % par niveau d'étable du maître." in page)
        assertTrue("Alliance : 408 000" in page)
        assertTrue("Reversé aux maîtres : 0" in page)
        assertTrue("Soldes positifs : 132 000" in page)
        assertTrue("3 envois : 132 000" in page)
    }

    @Test
    fun `members never receive the full detail`() = appTest {
        twoComputations()
        signIn("P1", chief = false)

        val page = page(Wages(detail = 1))

        assertFalse("TdC moyen" in page)
        assertFalse("Détail complet" in page)
        assertTrue("""<tr class="emp-table__you" data-pseudo="p1"><td>P1 (toi)</td>""" in page)
    }

    @Test
    fun `puts the viewer first and shows chiefs the full detail when asked`() = appTest {
        twoComputations()
        val history = wageHistory()

        val viewer = plain(createHTML().html { wagesPage(history, Viewer(2, "P2", chief = false, accessLinkId = 0)) })
        val chief = plain(createHTML().html { wagesPage(history, Viewer(3, "P3", chief = true, accessLinkId = 0)) })
        val detail = plain(createHTML().html { wagesPage(history, Viewer(3, "P3", chief = true, accessLinkId = 0), detail = true) })

        assertEquals("P2", deliveryRows(viewer).first()[0])
        assertTrue("""<span class="player-name giver__pseudo">P2<span class="emp-badge emp-badge--you">toi</span></span>""" in viewer)
        assertTrue("""<article class="emp-card giver giver--you" data-pseudo="p2">""" in viewer)
        assertTrue("toi, puis le plus gros reste" in viewer)
        assertTrue("""<tr class="emp-table__you" data-pseudo="p2"><td>P2 (toi)</td>""" in viewer)
        assertFalse("Détail complet" in viewer)
        // A chief reads the largest amount left first, their own included
        val chiefGiver = plain(createHTML().html { wagesPage(history, Viewer(2, "P2", chief = true, accessLinkId = 0)) })
        assertEquals("P1", deliveryRows(chiefGiver).first()[0])
        assertTrue("le plus gros reste d'abord" in chiefGiver)

        assertTrue("""<form action="/wages#balances" method="get" class="balances__toggle" data-submit-on-change=""><input type="hidden" name="id" value="2">""" in chief)
        assertTrue("""<input type="checkbox" name="detail" value="1">Détail complet<span class="emp-badge emp-badge--leader">Chef</span>""" in chief)
        assertFalse("TdC moyen" in chief)

        assertTrue("""name="detail" value="1" checked="checked"""" in detail)
        assertTrue("<th class=\"number\" scope=\"col\">TdC moyen</th><th class=\"number\" scope=\"col\">Production propre</th><th class=\"number\" scope=\"col\">Temps de ponte</th><th class=\"number\" scope=\"col\">Part des besoins</th><th class=\"number\" scope=\"col\">Part</th>" in detail)
        assertTrue("""<td>P1</td><td class="number">4 000</td><td class="number">192 000</td><td class="number">0</td><td class="number">25,00 %</td><td class="number">102 000</td><td class="number">−90 000</td>""" in detail)
        assertEquals(balanceRows(chief), balanceRows(detail))
    }
}
