package fr.nathanaudvard.emp.web.controllers

import fr.nathanaudvard.emp.domain.usecase.GetWageHistoryUseCase
import fr.nathanaudvard.emp.web.pages.gameTimeZone
import fr.nathanaudvard.emp.web.pages.wagesPage
import fr.nathanaudvard.emp.web.resources.Wages
import io.ktor.server.html.respondHtml
import io.ktor.server.resources.get
import io.ktor.server.routing.Route
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlin.time.Instant

fun Route.wagesController(getWageHistory: GetWageHistoryUseCase, allianceTag: String) {
    get<Wages> { page ->
        // The end of the date: its latest computation is the latest one before the next day.
        // A malformed date finds no computation, as a date before the first one
        val before = page.date?.let { date ->
            runCatching { LocalDate.parse(date) }.getOrNull()
                ?.plus(DatePeriod(days = 1))?.atStartOfDayIn(gameTimeZone)
                ?: Instant.DISTANT_PAST
        }
        // An unknown id is said on the page: StatusPages would replace a 404 page with its JSON error
        val history = getWageHistory(allianceTag, page.id, before)
        call.respondHtml { wagesPage(history, searchedDate = page.date.takeIf { page.id == null }) }
    }
}
