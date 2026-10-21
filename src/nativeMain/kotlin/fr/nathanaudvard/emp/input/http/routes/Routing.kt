package fr.nathanaudvard.emp.input.http.routes

import fr.nathanaudvard.emp.domain.usecase.SyncAlliances
import fr.nathanaudvard.emp.domain.usecase.SyncPlayers
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.plugins.di.resolve
import io.ktor.server.resources.Resources
import io.ktor.server.routing.routing

suspend fun Application.configureRouting() {
    val syncAlliances: SyncAlliances = dependencies.resolve()
    val syncPlayers: SyncPlayers = dependencies.resolve()

    routing {
        healthRoutes()
        allianceRoutes(syncAlliances)
        playerRoutes(syncPlayers)
    }
}
