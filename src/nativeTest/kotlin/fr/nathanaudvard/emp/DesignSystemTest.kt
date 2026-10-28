package fr.nathanaudvard.emp

import fr.nathanaudvard.emp.web.pages.BtnSize
import fr.nathanaudvard.emp.web.pages.BtnVariant
import fr.nathanaudvard.emp.web.pages.MessageVariant
import fr.nathanaudvard.emp.web.pages.empBtn
import fr.nathanaudvard.emp.web.pages.empMessage
import fr.nathanaudvard.emp.web.pages.empProgress
import fr.nathanaudvard.emp.web.resources.Home
import fr.nathanaudvard.emp.web.resources.Wages
import io.ktor.client.plugins.resources.get
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.html.div
import kotlinx.html.stream.createHTML
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesignSystemTest {
    private val stylesheetLinks = Regex("""<link href="(/assets/[^"]+)" rel="stylesheet">""")

    @Test
    fun `pages load the font then the tokens the components and the base styles without any colour of their own`() = appTest {
        syncAndSignIn()
        for (page in listOf(client.get(Home()).bodyAsText(), client.get(Wages()).bodyAsText())) {
            assertTrue("""<link href="https://fonts.googleapis.com/css2?family=Source+Sans+3:wght@400;600;700&amp;display=swap" rel="stylesheet">""" in page)
            val sheets = stylesheetLinks.findAll(page).map { it.groupValues[1] }.toList()
            assertEquals(listOf("tokens", "bundle", "base"), sheets.map { it.removePrefix("/assets/").substringBefore('-') })
            assertTrue(page.indexOf("data-theme") < page.indexOf(sheets.first()))
            assertFalse(Regex("""#[0-9a-fA-F]{3,8}\b""").containsMatchIn(page), "A colour is written in the page")
        }
    }

    @Test
    fun `stylesheets are cached for good under a name that changes with their content`() = appTest {
        syncAndSignIn()
        val sheets = stylesheetLinks.findAll(client.get(Home()).bodyAsText()).map { it.groupValues[1] }.toList()
        for (sheet in sheets) {
            assertTrue(Regex("""/assets/[a-z]+-[0-9a-f]{10}\.css""").matches(sheet), sheet)
            val res = client.get(sheet)
            assertEquals(HttpStatusCode.OK, res.status)
            assertEquals(ContentType.Text.CSS, res.contentType()?.withoutParameters())
            assertEquals("public, max-age=31536000, immutable", res.headers[HttpHeaders.CacheControl])
        }
        assertTrue("--surface-page: #f5f4f1;" in client.get(sheets[0]).bodyAsText())
        assertTrue(".emp-btn {" in client.get(sheets[1]).bodyAsText())
        assertEquals(HttpStatusCode.NotFound, client.get("/assets/tokens-0000000000.css").status)
    }

    @Test
    fun `components are written with their classes only`() {
        val html = createHTML(prettyPrint = false).div {
            empBtn(BtnVariant.SECONDARY, BtnSize.COMPACT) { +"J'ai envoyé" }
            empMessage(MessageVariant.DANGER) { +"Aucun convoi" }
            empProgress(2_000_000, 4_915_921, "Envoyé")
        }

        assertTrue("""<button type="submit" class="emp-btn emp-btn--secondary emp-btn--compact">J'ai envoyé</button>""" in html)
        assertTrue("""<div class="emp-message emp-message--danger" role="alert">Aucun convoi</div>""" in html)
        assertTrue("""role="progressbar" aria-label="Envoyé" aria-valuemin="0" aria-valuemax="100" aria-valuenow="40"""" in html)
        assertTrue("""<div class="emp-progress__bar" style="width: 40%"></div>""" in html)
    }
}
