package fr.nathanaudvard.emp.api.controllers

import fr.nathanaudvard.emp.api.dto.SyncRequest
import fr.nathanaudvard.emp.api.dto.toResponse
import fr.nathanaudvard.emp.api.resources.Exports
import fr.nathanaudvard.emp.domain.model.ExportVersion
import fr.nathanaudvard.emp.domain.usecase.SyncExportedAlliancesUseCase
import fr.nathanaudvard.emp.domain.usecase.SyncExportedPlayersUseCase
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.request.receive
import io.ktor.server.resources.post
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingCall

fun Route.exportController(syncAlliances: SyncExportedAlliancesUseCase, syncPlayers: SyncExportedPlayersUseCase) {
    post<Exports.Alliances.Sync> { sync ->
        call.respond(syncAlliances(ExportVersion.parse(sync.version), call.receiveTags()).toResponse())
    }
    post<Exports.Players.Sync> { sync ->
        call.respond(syncPlayers(ExportVersion.parse(sync.version), call.receiveTags()).toResponse())
    }
}

private suspend fun RoutingCall.receiveTags(): Set<String> {
    val tags = receive<SyncRequest>().tags.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    if (tags.isEmpty()) throw BadRequestException("At least one alliance tag is required")
    return tags
}
