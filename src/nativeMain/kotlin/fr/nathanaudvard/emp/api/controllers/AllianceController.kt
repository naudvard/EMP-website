package fr.nathanaudvard.emp.api.controllers

import fr.nathanaudvard.emp.api.dto.toResponse
import fr.nathanaudvard.emp.api.resources.Alliances
import fr.nathanaudvard.emp.domain.model.ExportVersion
import fr.nathanaudvard.emp.domain.usecase.SyncAlliances
import io.ktor.server.resources.post
import io.ktor.server.response.respond
import io.ktor.server.routing.Route

fun Route.allianceController(syncAlliances: SyncAlliances) {
    post<Alliances.Sync> { sync ->
        call.respond(syncAlliances(ExportVersion.parse(sync.version)).toResponse())
    }
}
