package fr.nathanaudvard.emp

import fr.nathanaudvard.emp.input.http.routes.Health
import io.ktor.client.plugins.resources.Resources
import io.ktor.client.plugins.resources.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

class HealthRoutesTest {
    @Test
    fun `answers ok`() = testApplication {
        application { module() }
        val client = createClient { install(Resources) }

        val res = client.get(Health())

        assertEquals(HttpStatusCode.OK, res.status)
        assertEquals("""{"status":"ok"}""", res.bodyAsText())
    }
}
