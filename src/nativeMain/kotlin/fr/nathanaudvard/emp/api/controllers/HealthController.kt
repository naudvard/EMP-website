package fr.nathanaudvard.emp.api.controllers

import fr.nathanaudvard.emp.api.dto.HealthResponse
import fr.nathanaudvard.emp.api.resources.Health
import io.ktor.server.resources.get
import io.ktor.server.response.respond
import io.ktor.server.routing.Route

fun Route.healthController() {
    get<Health> {
        call.respond(HealthResponse(status = "ok"))
    }
}
