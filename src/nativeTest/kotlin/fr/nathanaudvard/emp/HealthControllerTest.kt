package fr.nathanaudvard.emp

import fr.nathanaudvard.emp.api.resources.Health
import io.ktor.client.plugins.resources.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals

class HealthControllerTest {
    @Test
    fun `answers ok`() = syncTest {
        val res = client.get(Health())

        assertEquals(HttpStatusCode.OK, res.status)
        assertEquals("""{"status":"ok"}""", res.bodyAsText())
    }
}
