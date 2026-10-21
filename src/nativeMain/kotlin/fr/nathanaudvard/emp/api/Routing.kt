package fr.nathanaudvard.emp.api

import fr.nathanaudvard.emp.api.controllers.allianceController
import fr.nathanaudvard.emp.api.controllers.healthController
import fr.nathanaudvard.emp.api.controllers.playerController
import fr.nathanaudvard.emp.domain.usecase.SyncAlliances
import fr.nathanaudvard.emp.domain.usecase.SyncPlayers
import io.ktor.server.application.Application
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.plugins.di.resolve
import io.ktor.server.routing.routing

suspend fun Application.configureRouting() {
    val syncAlliances: SyncAlliances = dependencies.resolve()
    val syncPlayers: SyncPlayers = dependencies.resolve()

    routing {
        healthController()
        allianceController(syncAlliances)
        playerController(syncPlayers)
    }
}
