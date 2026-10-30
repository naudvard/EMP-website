package fr.nathanaudvard.emp.web.controllers

import fr.nathanaudvard.emp.config.viewer
import fr.nathanaudvard.emp.domain.service.AllianceService
import fr.nathanaudvard.emp.web.pages.AllianceView
import fr.nathanaudvard.emp.web.pages.MemberFilter
import fr.nathanaudvard.emp.web.pages.alliancePage
import fr.nathanaudvard.emp.web.resources.Alliance
import io.ktor.server.html.respondHtml
import io.ktor.server.resources.get
import io.ktor.server.routing.Route
import kotlin.time.Clock

fun Route.alliancePageController(
    alliance: AllianceService,
    clock: Clock,
    allianceTag: String,
) {
    get<Alliance> { page ->
        val view = AllianceView(filter = MemberFilter.of(page.filter))
        val overview = alliance.overview(allianceTag)
        call.respondHtml { alliancePage(allianceTag, call.viewer, overview, clock.now(), view) }
    }
}
