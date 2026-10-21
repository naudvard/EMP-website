package fr.nathanaudvard.emp.api.controllers

import fr.nathanaudvard.emp.api.dto.toResponse
import fr.nathanaudvard.emp.api.resources.Exports
import fr.nathanaudvard.emp.domain.model.ExportVersion
import fr.nathanaudvard.emp.domain.usecase.SyncExportedAlliances
import fr.nathanaudvard.emp.domain.usecase.SyncExportedPlayers
import io.ktor.server.resources.post
import io.ktor.server.response.respond
import io.ktor.server.routing.Route

fun Route.exportController(syncAlliances: SyncExportedAlliances, syncPlayers: SyncExportedPlayers) {
    post<Exports.Alliances.Sync> { sync ->
        call.respond(syncAlliances(ExportVersion.parse(sync.version)).toResponse())
    }
    post<Exports.Players.Sync> { sync ->
        call.respond(syncPlayers(ExportVersion.parse(sync.version)).toResponse())
    }
}
