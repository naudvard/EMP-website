package fr.nathanaudvard.emp.web.controllers

import fr.nathanaudvard.emp.database.WageRepository
import fr.nathanaudvard.emp.web.pages.wagesPage
import fr.nathanaudvard.emp.web.resources.Wages
import io.ktor.server.html.respondHtml
import io.ktor.server.resources.get
import io.ktor.server.routing.Route

fun Route.wagesController(wages: WageRepository, allianceTag: String) {
    get<Wages> {
        val computation = wages.latest(allianceTag)
        call.respondHtml { wagesPage(computation) }
    }
}
