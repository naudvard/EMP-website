package fr.nathanaudvard.emp.input.http.routes

import fr.nathanaudvard.emp.domain.model.ExportVersion
import fr.nathanaudvard.emp.domain.usecase.SyncPlayers
import fr.nathanaudvard.emp.input.http.dto.toResponse
import io.ktor.server.resources.post
import io.ktor.server.response.respond
import io.ktor.server.routing.Route

fun Route.playerRoutes(syncPlayers: SyncPlayers) {
    post<ApiV1.Players.Sync> { sync ->
        call.respond(syncPlayers(ExportVersion.parse(sync.version)).map { it.toResponse() })
    }
}
