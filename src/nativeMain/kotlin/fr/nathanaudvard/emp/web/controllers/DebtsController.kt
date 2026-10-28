package fr.nathanaudvard.emp.web.controllers

import fr.nathanaudvard.emp.config.requireChief
import fr.nathanaudvard.emp.config.viewer
import fr.nathanaudvard.emp.domain.usecase.GetAllianceMembersUseCase
import fr.nathanaudvard.emp.domain.usecase.GetDebtsUseCase
import fr.nathanaudvard.emp.domain.usecase.GetWageSettlementsUseCase
import fr.nathanaudvard.emp.domain.usecase.SaveWageSettlementsUseCase
import fr.nathanaudvard.emp.web.forms.DebtsForm
import fr.nathanaudvard.emp.web.forms.DebtsValidation
import fr.nathanaudvard.emp.web.pages.debtsPage
import fr.nathanaudvard.emp.web.resources.Debts
import io.ktor.http.HttpStatusCode
import io.ktor.server.html.respondHtml
import io.ktor.server.request.receiveParameters
import io.ktor.server.resources.get
import io.ktor.server.resources.href
import io.ktor.server.resources.post
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route

fun Route.debtsController(
    getMembers: GetAllianceMembersUseCase,
    getSettlements: GetWageSettlementsUseCase,
    getDebts: GetDebtsUseCase,
    saveSettlements: SaveWageSettlementsUseCase,
    allianceTag: String
) {
    get<Debts> { page ->
        val members = getMembers(allianceTag)
        val settlements = getSettlements(members)
        val debts = getDebts(members)
        call.respondHtml {
            debtsPage(call.application.href(Debts()), call.viewer, members, settlements, debts, saved = page.saved == true)
        }
    }

    post<Debts> {
        val chief = call.requireChief()
        val members = getMembers(allianceTag)
        when (val result = DebtsForm.from(call.receiveParameters()).validate(members)) {
            is DebtsValidation.Valid -> {
                saveSettlements(result.settlements)
                call.respondRedirect(call.application.href(Debts(saved = true)))
            }

            is DebtsValidation.Invalid -> {
                val settlements = getSettlements(members)
                val debts = getDebts(members)
                call.respondHtml(HttpStatusCode.BadRequest) {
                    debtsPage(call.application.href(Debts()), chief, members, settlements, debts, result.error)
                }
            }
        }
    }
}
