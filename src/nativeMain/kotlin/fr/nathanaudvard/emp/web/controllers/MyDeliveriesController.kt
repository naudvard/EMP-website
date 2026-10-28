package fr.nathanaudvard.emp.web.controllers

import fr.nathanaudvard.emp.config.requireEdit
import fr.nathanaudvard.emp.config.viewer
import fr.nathanaudvard.emp.domain.usecase.DeleteConvoyUseCase
import fr.nathanaudvard.emp.domain.usecase.GetWageHistoryUseCase
import fr.nathanaudvard.emp.domain.usecase.RecordConvoysUseCase
import fr.nathanaudvard.emp.web.forms.ConvoyForm
import fr.nathanaudvard.emp.web.forms.ConvoyValidation
import fr.nathanaudvard.emp.web.pages.CONVOYS
import fr.nathanaudvard.emp.web.pages.myDeliveriesPage
import fr.nathanaudvard.emp.web.resources.MyDeliveries
import io.ktor.http.HttpStatusCode
import io.ktor.server.html.respondHtml
import io.ktor.server.request.receiveParameters
import io.ktor.server.resources.get
import io.ktor.server.resources.href
import io.ktor.server.resources.post
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route

// Without a pseudo, the viewer's deliveries. Everyone sees everyone's, only a chief records or removes others' convoys
fun Route.myDeliveriesController(
    getWageHistory: GetWageHistoryUseCase,
    recordConvoys: RecordConvoysUseCase,
    deleteConvoy: DeleteConvoyUseCase,
    allianceTag: String,
) {
    get<MyDeliveries> { page ->
        val viewer = call.viewer
        val latest = getWageHistory(allianceTag).selected
        call.respondHtml { myDeliveriesPage(latest, page.pseudo.cleaned() ?: viewer.pseudo, viewer, recorded = page.recorded) }
    }

    // Convoys are recorded towards the latest computation, the one the page shows
    post<MyDeliveries> { page ->
        val viewer = call.viewer
        val history = getWageHistory(allianceTag)
        val computationId = history.selectedId
        val latest = history.selected
        val pseudo = page.pseudo.cleaned() ?: viewer.pseudo
        val sender = latest?.wage(pseudo)
        if (computationId == null || latest == null || sender == null) {
            // The page says why: no computation, or a pseudo not in it
            call.respondHtml(HttpStatusCode.BadRequest) { myDeliveriesPage(latest, pseudo, viewer) }
            return@post
        }
        call.requireEdit(sender.playerId)
        val form = ConvoyForm.from(call.receiveParameters())
        when (val result = form.validate(latest, sender)) {
            is ConvoyValidation.Valid -> {
                recordConvoys(computationId, result.convoys)
                // Back to the convoys, where their confirmation is
                val page = call.application.href(MyDeliveries(sender.pseudo, recorded = result.convoys.size))
                call.respondRedirect("$page#$CONVOYS")
            }

            is ConvoyValidation.Invalid -> call.respondHtml(HttpStatusCode.BadRequest) {
                myDeliveriesPage(latest, pseudo, viewer, form, result.error)
            }
        }
    }

    post<MyDeliveries.DeleteConvoy> { action ->
        val pseudo = action.parent.pseudo.cleaned() ?: call.viewer.pseudo
        val sender = getWageHistory(allianceTag).selected?.wage(pseudo)
        if (sender != null) {
            call.requireEdit(sender.playerId)
            deleteConvoy(action.id, sender.playerId)
        }
        call.respondRedirect("${call.application.href(MyDeliveries(sender?.pseudo ?: pseudo))}#$CONVOYS")
    }
}

private fun String?.cleaned() = this?.trim()?.takeIf { it.isNotEmpty() }
