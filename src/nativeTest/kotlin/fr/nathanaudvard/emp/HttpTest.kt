package fr.nathanaudvard.emp

import fr.nathanaudvard.emp.config.MAX_BODY_SIZE
import fr.nathanaudvard.emp.web.resources.Access
import io.ktor.client.plugins.resources.get
import io.ktor.client.request.forms.submitForm
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import kotlin.test.Test
import kotlin.test.assertEquals

class HttpTest {
    @Test
    fun `every page forbids being framed and keeps its address to the site`() = appTest {
        val res = client.get(Access())

        assertEquals("nosniff", res.headers["X-Content-Type-Options"])
        assertEquals("same-origin", res.headers["Referrer-Policy"])
        assertEquals("DENY", res.headers["X-Frame-Options"])
    }

    @Test
    fun `a body over the limit is refused`() = appTest {
        syncAndSignIn()

        val res = client.submitForm("/me/convoys", parameters { append("message", "x".repeat(MAX_BODY_SIZE.toInt())) })

        assertEquals(HttpStatusCode.PayloadTooLarge, res.status)
        assertEquals(listOf(0L), query("SELECT COUNT(*) FROM convoys") { it.getLong(0)!! })
    }
}
