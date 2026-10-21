package fr.nathanaudvard.emp.api.controllers

import fr.nathanaudvard.emp.api.dto.toResponse
import fr.nathanaudvard.emp.api.resources.Players
import fr.nathanaudvard.emp.domain.model.ExportVersion
import fr.nathanaudvard.emp.domain.usecase.SyncPlayers
import io.ktor.server.resources.post
import io.ktor.server.response.respond
import io.ktor.server.routing.Route

fun Route.playerController(syncPlayers: SyncPlayers) {
    post<Players.Sync> { sync ->
        call.respond(syncPlayers(ExportVersion.parse(sync.version)).toResponse())
    }
}
