package fr.nathanaudvard.emp

import fr.nathanaudvard.emp.api.resources.Alliances
import fr.nathanaudvard.emp.web.resources.Wages
import io.ktor.client.plugins.resources.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WagesPageTest {
    private fun deliveryRows(page: String) =
        Regex("""<tr[^>]*>\s*<td>([^<]*)</td>\s*<td>([^<]*)</td>\s*<td class="amount">([^<]*)</td>""")
            .findAll(page).map { it.destructured.toList() }.toList()

    private fun summaryRows(page: String) =
        Regex("""<tr>\s*<td>([^<]*)</td>\s*<td class="amount">([^<]*)</td>\s*<td class="amount">([^<]*)</td>""")
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
        fourmizzz.players("202610201200", fields.map { (id, field) -> fourmi.copy(id = id, pseudo = "P$id", field = field) })
        post(Alliances.Tag.Wages(Alliances.Tag(tag = "EMP"), distribution = "proportional"))
        post(Alliances.Tag.Wages(Alliances.Tag(tag = "EMP"), distribution = "minimal"))

        val page = client.get(Wages()).bodyAsText()

        assertTrue("Calculé le 20/10/2026 à 14:30 · 1 h de mesures sur 24 · répartition minimale" in page)
        assertEquals(
            listOf(
                listOf("P1", "P3", "78 000"),
                listOf("P1", "P4", "12 000"),
                listOf("P2", "P4", "42 000"),
            ),
            deliveryRows(page),
        )
        assertEquals(
            listOf(
                listOf("P1", "—", "90 000"),
                listOf("P2", "—", "42 000"),
                listOf("P3", "78 000", "—"),
                listOf("P4", "54 000", "—"),
            ),
            summaryRows(page),
        )
    }

    @Test
    fun `steps between past computations`() = appTest {
        fourmizzz.players("202610201200", listOf(fourmi.copy(id = 1, pseudo = "P1"), fourmi.copy(id = 2, pseudo = "P2")))
        repeat(3) { post(Alliances.Tag.Wages(Alliances.Tag(tag = "EMP"), distribution = "proportional")) }

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
        fourmizzz.players("202610201200", listOf(fourmi.copy(id = 1, pseudo = "P1")))
        post(Alliances.Tag.Wages(Alliances.Tag(tag = "EMP"), distribution = "proportional"))
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
    }
}
