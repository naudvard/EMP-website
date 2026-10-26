package fr.nathanaudvard.emp

import fr.nathanaudvard.emp.api.resources.Alliances
import fr.nathanaudvard.emp.api.resources.Exports
import fr.nathanaudvard.emp.web.resources.Home
import io.ktor.client.plugins.resources.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HomePageTest {
    @Test
    fun `links to the pages when nothing is synced yet`() = appTest {
        val res = client.get(Home())

        assertEquals(HttpStatusCode.OK, res.status)
        val page = res.bodyAsText()
        assertTrue("""<a href="/" aria-current="page">Accueil</a>""" in page)
        assertTrue("""<a href="/wages">Salaires</a>""" in page)
        assertTrue("""<a href="/update-levels">Mes niveaux</a>""" in page)
        assertTrue("Aucun calcul de salaires pour l'instant." in page)
        assertTrue("Aucun membre synchronisé" in page)
    }

    @Test
    fun `summarizes the latest computation and the members`() = appTest {
        fourmizzz.players("202610201200", listOf(fourmi.copy(id = 1, pseudo = "P1", field = 4_000), fourmi.copy(id = 2, pseudo = "P2", field = 500)))
        post(Exports.Players.Sync(version = "202610201200"))
        post(Alliances.Tag.Wages(Alliances.Tag(tag = "EMP"), distribution = "minimal"))

        val page = client.get(Home()).bodyAsText()

        assertTrue("Dernier calcul le 20/10/2026 à 14:30" in page)
        assertTrue("2 joueurs · répartition minimale" in page)
        assertTrue("2 membres dans l'alliance" in page)
    }
}
