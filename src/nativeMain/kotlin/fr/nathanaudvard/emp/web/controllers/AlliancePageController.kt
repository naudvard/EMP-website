package fr.nathanaudvard.emp.web.controllers

import fr.nathanaudvard.emp.config.requireChief
import fr.nathanaudvard.emp.config.viewer
import fr.nathanaudvard.emp.domain.exception.ExportNotFoundException
import fr.nathanaudvard.emp.domain.exception.ExportUnavailableException
import fr.nathanaudvard.emp.domain.usecase.GetAllianceUseCase
import fr.nathanaudvard.emp.domain.usecase.SyncFourmizzzUseCase
import fr.nathanaudvard.emp.web.pages.AllianceView
import fr.nathanaudvard.emp.web.pages.MemberFilter
import fr.nathanaudvard.emp.web.pages.alliancePage
import fr.nathanaudvard.emp.web.resources.Alliance
import io.ktor.http.HttpStatusCode
import io.ktor.server.html.respondHtml
import io.ktor.server.resources.get
import io.ktor.server.resources.href
import io.ktor.server.resources.post
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import kotlin.time.Clock

fun Route.alliancePageController(
    getAlliance: GetAllianceUseCase,
    syncFourmizzz: SyncFourmizzzUseCase,
    clock: Clock,
    allianceTag: String,
) {
    get<Alliance> { page ->
        val view = AllianceView(
            filter = MemberFilter.of(page.filter),
            pseudo = page.pseudo?.trim().orEmpty(),
            synced = page.synced,
            settled = page.settled,
            previous = page.previous,
        )
        val overview = getAlliance(allianceTag)
        call.respondHtml { alliancePage(allianceTag, call.viewer, overview, clock.now(), view) }
    }

    // The failure is said on the page: a redirect could not tell it
    post<Alliance.Sync> {
        val chief = call.requireChief()
        val error = try {
            val result = syncFourmizzz(allianceTag)
            return@post call.respondRedirect(call.application.href(Alliance(synced = result.count)))
        } catch (_: ExportNotFoundException) {
            "Fourmizzz n'a pas d'export à synchroniser pour l'instant."
        } catch (_: ExportUnavailableException) {
            "Fourmizzz ne répond pas : rien n'a été synchronisé. Réessaie dans quelques minutes."
        }
        val overview = getAlliance(allianceTag)
        call.respondHtml(HttpStatusCode.BadGateway) {
            alliancePage(allianceTag, chief, overview, clock.now(), AllianceView(syncError = error))
        }
    }
}
