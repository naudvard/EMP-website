package fr.nathanaudvard.emp.web.controllers

import fr.nathanaudvard.emp.config.requireChief
import fr.nathanaudvard.emp.config.viewer
import fr.nathanaudvard.emp.domain.model.WageDistribution
import fr.nathanaudvard.emp.domain.model.gameTimeZone
import fr.nathanaudvard.emp.domain.service.WageService
import fr.nathanaudvard.emp.web.pages.WagesFields
import fr.nathanaudvard.emp.web.pages.wagesPage
import fr.nathanaudvard.emp.web.pages.wagesPanel
import fr.nathanaudvard.emp.web.resources.Wages
import fr.nathanaudvard.emp.web.respondSections
import io.ktor.server.html.respondHtml
import io.ktor.server.htmx.hx
import io.ktor.server.request.receiveParameters
import io.ktor.server.resources.get
import io.ktor.server.resources.post
import io.ktor.server.routing.Route
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlin.time.Clock
import kotlin.time.Instant

fun Route.wagesController(
    wages: WageService,
    clock: Clock,
    allianceTag: String,
) {
    // The panel is only ever shown to a chief
    get<Wages> { page ->
        // The end of the date: its latest computation is the latest one before the next day.
        // A malformed date finds no computation, as a date before the first one
        val before = page.date?.let { date ->
            runCatching { LocalDate.parse(date) }.getOrNull()
                ?.plus(DatePeriod(days = 1))?.atStartOfDayIn(gameTimeZone)
                ?: Instant.DISTANT_PAST
        }
        // An unknown id is said on the page: StatusPages would replace a 404 page with its JSON error
        val history = wages.history(allianceTag, page.id, before)
        val viewer = call.viewer
        val panel = if (viewer.chief) wages.panel(allianceTag) else null
        call.respondHtml {
            wagesPage(
                history,
                viewer,
                searchedDate = page.date.takeIf { page.id == null },
                detail = page.detail == 1,
                panel = panel,
                now = clock.now(),
            )
        }
    }

    // The panel of the computation the chief is looking at
    hx {
        post<Wages.Settings> {
            val chief = call.requireChief()
            val parameters = call.receiveParameters()
            // Without the time field, the time saved is kept
            val time = parameters[WagesFields.TIME]?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
                ?: wages.settings(allianceTag).computationTime.takeIf { WagesFields.TIME !in parameters }
            val distribution = WageDistribution.entries.find { it.name == parameters[WagesFields.DISTRIBUTION] }
            if (time != null && distribution != null) {
                wages.saveSettings(allianceTag, LocalTime(time.hour, time.minute), distribution, chief.playerId)
            }
            val history = wages.history(allianceTag, parameters[WagesFields.ID]?.toLongOrNull())
            val panel = wages.panel(allianceTag)
            call.respondSections { wagesPanel(panel, history, clock.now()) }
        }
    }
}
