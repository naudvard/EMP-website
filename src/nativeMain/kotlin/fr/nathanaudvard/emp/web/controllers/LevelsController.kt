package fr.nathanaudvard.emp.web.controllers

import fr.nathanaudvard.emp.domain.usecase.GetAllianceMembersUseCase
import fr.nathanaudvard.emp.domain.usecase.SavePlayerLevelUseCase
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

fun Route.levelsController(
    getMembers: GetAllianceMembersUseCase,
    saveLevel: SavePlayerLevelUseCase,
    allianceTag: String
) {
    get<Levels> { page ->
        val members = getMembers(allianceTag)
        // The pseudo comes from the URL: only a member's can be confirmed
        val saved = page.saved?.takeIf { saved -> members.any { it.pseudo == saved } }
        call.respondHtml {
            // A player often saves several levels in a row: their pseudo stays filled in
            levelsPage(call.application.href(Levels()), members, LevelsForm(pseudo = saved.orEmpty()), saved = saved)
        }
    }

    post<Levels> {
        val members = getMembers(allianceTag)
        val form = LevelsForm.from(call.receiveParameters())
        when (val result = form.validate(members)) {
            is LevelsValidation.Valid -> {
                saveLevel(result.member.id, result.kind, result.level)
                call.respondRedirect(call.application.href(Levels(saved = result.member.pseudo)))
            }

            is LevelsValidation.Invalid -> call.respondHtml(HttpStatusCode.BadRequest) {
                levelsPage(call.application.href(Levels()), members, form, result.errors)
            }
        }
    }
}
