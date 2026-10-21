package fr.nathanaudvard.emp

import fr.nathanaudvard.emp.api.resources.Alliances
import fr.nathanaudvard.emp.web.resources.Wages
import io.ktor.client.plugins.resources.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WagesPageTest {
    private fun deliveryRows(page: String) =
        Regex("""<tr>\s*<td>([^<]*)</td>\s*<td>([^<]*)</td>\s*<td class="amount">([^<]*)</td>""")
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
}
