package fr.nathanaudvard.emp.input.http.routes

import fr.nathanaudvard.emp.domain.model.ExportVersion
import fr.nathanaudvard.emp.domain.usecase.SyncAlliances
import fr.nathanaudvard.emp.input.http.dto.toResponse
import io.ktor.server.resources.post
import io.ktor.server.response.respond
import io.ktor.server.routing.Route

fun Route.allianceRoutes(syncAlliances: SyncAlliances) {
    post<ApiV1.Alliances.Sync> { sync ->
        call.respond(syncAlliances(ExportVersion.parse(sync.version)).map { it.toResponse() })
    }
}
