package fr.nathanaudvard.emp.input.http.routes

import fr.nathanaudvard.emp.input.http.dto.HealthResponse
import io.ktor.resources.Resource
import io.ktor.server.resources.get
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import kotlinx.serialization.Serializable

@Serializable
@Resource("/health")
class Health

fun Route.healthRoutes() {
    get<Health> {
        call.respond(HealthResponse(status = "ok"))
    }
}
