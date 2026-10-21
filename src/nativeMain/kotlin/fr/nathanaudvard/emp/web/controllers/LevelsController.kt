package fr.nathanaudvard.emp.web.controllers

import fr.nathanaudvard.emp.database.ExportedPlayerRepository
import fr.nathanaudvard.emp.database.PlayerRepository
import fr.nathanaudvard.emp.web.forms.LevelsForm
import fr.nathanaudvard.emp.web.forms.LevelsValidation
import fr.nathanaudvard.emp.web.pages.levelsPage
import fr.nathanaudvard.emp.web.resources.Levels
import io.ktor.http.HttpStatusCode
import io.ktor.server.html.respondHtml
import io.ktor.server.request.receiveParameters
import io.ktor.server.resources.get
import io.ktor.server.resources.href
import io.ktor.server.resources.post
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route

fun Route.levelsController(exportedPlayers: ExportedPlayerRepository, players: PlayerRepository, allianceTag: String) {
    get<Levels> { page ->
        val members = exportedPlayers.latestMembers(allianceTag)
        call.respondHtml { levelsPage(call.application.href(Levels()), members, LevelsForm(), saved = page.saved) }
    }

    post<Levels> {
        val members = exportedPlayers.latestMembers(allianceTag)
        val form = LevelsForm.from(call.receiveParameters())
        when (val result = form.validate(members)) {
            is LevelsValidation.Valid -> {
                players.saveLevel(result.member.id, result.kind, result.level)
                call.respondRedirect(call.application.href(Levels(saved = result.member.pseudo)))
            }
            is LevelsValidation.Invalid -> call.respondHtml(HttpStatusCode.BadRequest) {
                levelsPage(call.application.href(Levels()), members, form, result.errors)
            }
        }
    }
}
