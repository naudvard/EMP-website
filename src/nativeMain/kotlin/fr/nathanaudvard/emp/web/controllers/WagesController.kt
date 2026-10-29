package fr.nathanaudvard.emp.web.controllers

import fr.nathanaudvard.emp.config.requireChief
import fr.nathanaudvard.emp.config.viewer
import fr.nathanaudvard.emp.domain.model.ComputationFailure
import fr.nathanaudvard.emp.domain.model.WageDistribution
import fr.nathanaudvard.emp.domain.model.gameTimeZone
import fr.nathanaudvard.emp.domain.usecase.ComputationResult
import fr.nathanaudvard.emp.domain.usecase.ComputeWagesNowUseCase
import fr.nathanaudvard.emp.domain.usecase.GetAllianceSettingsUseCase
import fr.nathanaudvard.emp.domain.usecase.GetWageHistoryUseCase
import fr.nathanaudvard.emp.domain.usecase.GetWagesPanelUseCase
import fr.nathanaudvard.emp.domain.usecase.SaveAllianceSettingsUseCase
import fr.nathanaudvard.emp.web.pages.CopiedText
import fr.nathanaudvard.emp.web.pages.PANEL_SECTION
import fr.nathanaudvard.emp.web.pages.WagesFields
import fr.nathanaudvard.emp.web.pages.announcementText
import fr.nathanaudvard.emp.web.pages.computePage
import fr.nathanaudvard.emp.web.pages.reminderText
import fr.nathanaudvard.emp.web.pages.wagesPage
import fr.nathanaudvard.emp.web.resources.Me
import fr.nathanaudvard.emp.web.resources.Wages
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.log
import io.ktor.server.html.respondHtml
import io.ktor.server.request.accept
import io.ktor.server.request.receiveParameters
import io.ktor.server.resources.get
import io.ktor.server.resources.href
import io.ktor.server.resources.post
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlin.time.Clock
import kotlin.time.Instant

fun Route.wagesController(
    getWageHistory: GetWageHistoryUseCase,
    getPanel: GetWagesPanelUseCase,
    getSettings: GetAllianceSettingsUseCase,
    saveSettings: SaveAllianceSettingsUseCase,
    computeNow: ComputeWagesNowUseCase,
    clock: Clock,
    publicUrl: String,
    allianceTag: String,
) {
    // The panel and the texts to copy are only ever shown to a chief
    suspend fun ApplicationCall.respondWages(page: Wages, copied: CopiedText? = null) {
        // The end of the date: its latest computation is the latest one before the next day.
        // A malformed date finds no computation, as a date before the first one
        val before = page.date?.let { date ->
            runCatching { LocalDate.parse(date) }.getOrNull()
                ?.plus(DatePeriod(days = 1))?.atStartOfDayIn(gameTimeZone)
                ?: Instant.DISTANT_PAST
        }
        // An unknown id is said on the page: StatusPages would replace a 404 page with its JSON error
        val history = getWageHistory(allianceTag, page.id, before)
        val viewer = viewer
        val panel = if (viewer.chief) getPanel(allianceTag) else null
        respondHtml {
            wagesPage(
                history,
                viewer,
                searchedDate = page.date.takeIf { page.id == null },
                detail = page.detail == 1,
                panel = panel,
                now = clock.now(),
                copied = copied,
            )
        }
    }

    // The copy button's script asks for the text alone; without it, the page shows the text to copy by hand
    suspend fun ApplicationCall.respondCopy(text: String, label: String, computationId: Long) {
        if (request.accept()?.contains(ContentType.Text.Plain.toString()) == true) {
            respondText(text)
        } else {
            respondWages(Wages(id = computationId), CopiedText(label, text))
        }
    }

    get<Wages> { page -> call.respondWages(page) }

    // Back to the computation the chief was looking at
    post<Wages.Settings> {
        val chief = call.requireChief()
        val parameters = call.receiveParameters()
        val time = parameters[WagesFields.TIME]?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
        val distribution = WageDistribution.entries.find { it.name == parameters[WagesFields.DISTRIBUTION] }
        if (time != null && distribution != null) {
            saveSettings(allianceTag, LocalTime(time.hour, time.minute), distribution, chief.playerId)
        }
        val page = Wages(id = parameters[WagesFields.ID]?.toLongOrNull())
        call.respondRedirect("${call.application.href(page)}#$PANEL_SECTION")
    }

    get<Wages.Compute> {
        val chief = call.requireChief()
        val settings = getSettings(allianceTag)
        call.respondHtml { computePage(chief, settings, clock.now()) }
    }

    // The new computation is the latest: Salaires and each member's page show it at once. A failure is said on the
    // confirmation, for the chief to try again
    post<Wages.Compute> {
        val chief = call.requireChief()
        when (val result = computeNow(allianceTag)) {
            is ComputationResult.Computed -> call.respondRedirect(call.application.href(Wages()))
            is ComputationResult.Failed -> {
                val status = when (result.failure) {
                    ComputationFailure.FOURMIZZZ_UNAVAILABLE -> HttpStatusCode.BadGateway
                    ComputationFailure.UNEXPECTED -> HttpStatusCode.InternalServerError
                    else -> HttpStatusCode.Conflict
                }
                if (result.failure == ComputationFailure.UNEXPECTED) call.application.log.error("Wages of $allianceTag", result.cause)
                val settings = getSettings(allianceTag)
                call.respondHtml(status) { computePage(chief, settings, clock.now(), result.failure) }
            }
        }
    }

    post<Wages.Reminder> { action ->
        call.requireChief()
        val computation = getWageHistory(allianceTag, action.computationId).selected
        val text = computation?.let {
            val next = getSettings(allianceTag).nextComputation(clock.now())
            reminderText(it, action.playerId, next, call.publicUrl(publicUrl, call.application.href(Me())))
        } ?: return@post call.respondRedirect(call.application.href(Wages(id = action.computationId)))
        val pseudo = computation.wages.single { it.playerId == action.playerId }.pseudo
        call.respondCopy(text, "Rappel pour $pseudo, en message privé", action.computationId)
    }

    post<Wages.Announcement> { action ->
        call.requireChief()
        val computation = getWageHistory(allianceTag, action.computationId).selected
            ?: return@post call.respondRedirect(call.application.href(Wages()))
        call.respondCopy(announcementText(computation), "Annonce pour le forum de l'alliance", action.computationId)
    }
}
