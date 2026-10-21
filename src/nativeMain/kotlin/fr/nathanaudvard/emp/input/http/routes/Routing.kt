package fr.nathanaudvard.emp.input.http.routes

import io.ktor.server.application.Application
import io.ktor.server.routing.routing

fun Application.configureRouting() {
    routing {
        healthRoutes()
    }
}
