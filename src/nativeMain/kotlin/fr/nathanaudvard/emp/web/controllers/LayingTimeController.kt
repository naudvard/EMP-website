package fr.nathanaudvard.emp.web.controllers

import fr.nathanaudvard.emp.database.ExportedPlayerRepository
import fr.nathanaudvard.emp.database.PlayerRepository
import fr.nathanaudvard.emp.web.forms.LayingTimeForm
import fr.nathanaudvard.emp.web.forms.LayingTimeValidation
import fr.nathanaudvard.emp.web.pages.layingTimePage
import fr.nathanaudvard.emp.web.resources.LayingTime
import io.ktor.http.HttpStatusCode
import io.ktor.server.html.respondHtml
import io.ktor.server.request.receiveParameters
import io.ktor.server.resources.get
import io.ktor.server.resources.href
import io.ktor.server.resources.post
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route

fun Route.layingTimeController(exportedPlayers: ExportedPlayerRepository, players: PlayerRepository, allianceTag: String) {
    get<LayingTime> { page ->
        val members = exportedPlayers.latestMembers(allianceTag)
        call.respondHtml { layingTimePage(call.application.href(LayingTime()), members, LayingTimeForm(), saved = page.saved) }
    }

    post<LayingTime> {
        val members = exportedPlayers.latestMembers(allianceTag)
        val form = LayingTimeForm.from(call.receiveParameters())
        when (val result = form.validate(members)) {
            is LayingTimeValidation.Valid -> {
                players.saveLevel(result.member.id, result.factor, result.level)
                call.respondRedirect(call.application.href(LayingTime(saved = result.member.pseudo)))
            }
            is LayingTimeValidation.Invalid -> call.respondHtml(HttpStatusCode.BadRequest) {
                layingTimePage(call.application.href(LayingTime()), members, form, result.errors)
            }
        }
    }
}
