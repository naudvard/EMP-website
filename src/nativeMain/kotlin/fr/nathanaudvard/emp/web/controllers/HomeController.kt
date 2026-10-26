package fr.nathanaudvard.emp.web.controllers

import fr.nathanaudvard.emp.domain.usecase.GetAllianceMembersUseCase
import fr.nathanaudvard.emp.domain.usecase.GetWageHistoryUseCase
import fr.nathanaudvard.emp.web.pages.homePage
import fr.nathanaudvard.emp.web.resources.Home
import io.ktor.server.html.respondHtml
import io.ktor.server.resources.get
import io.ktor.server.routing.Route

fun Route.homeController(
    getWageHistory: GetWageHistoryUseCase,
    getMembers: GetAllianceMembersUseCase,
    allianceTag: String
) {
    get<Home> {
        val latest = getWageHistory(allianceTag).selected
        val members = getMembers(allianceTag)
        call.respondHtml { homePage(allianceTag, latest, members.size) }
    }
}
