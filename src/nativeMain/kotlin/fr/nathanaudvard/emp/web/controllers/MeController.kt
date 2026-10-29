package fr.nathanaudvard.emp.web.controllers

import fr.nathanaudvard.emp.config.viewer
import fr.nathanaudvard.emp.domain.model.PlayerLevel
import fr.nathanaudvard.emp.domain.usecase.ConfirmPlayerLevelsUseCase
import fr.nathanaudvard.emp.domain.usecase.DeclareConvoyUseCase
import fr.nathanaudvard.emp.domain.usecase.DeleteConvoyUseCase
import fr.nathanaudvard.emp.domain.usecase.GetMyOverviewUseCase
import fr.nathanaudvard.emp.domain.usecase.PasteConvoysUseCase
import fr.nathanaudvard.emp.domain.usecase.SavePlayerLevelUseCase
import fr.nathanaudvard.emp.domain.usecase.UndoLevelChangeUseCase
import fr.nathanaudvard.emp.web.pages.MeFields
import fr.nathanaudvard.emp.web.pages.MeSection
import fr.nathanaudvard.emp.web.pages.PasteForm
import fr.nathanaudvard.emp.web.pages.mePage
import fr.nathanaudvard.emp.web.resources.Me
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.html.respondHtml
import io.ktor.server.request.receiveParameters
import io.ktor.server.resources.get
import io.ktor.server.resources.href
import io.ktor.server.resources.post
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import kotlin.time.Clock

// The viewer's own page: everything posted here acts for the viewer. Without JavaScript each post comes back to the
// page, at the section it changed; the shared script fetches the same answer and replaces the sections
fun Route.meController(
    getOverview: GetMyOverviewUseCase,
    saveLevel: SavePlayerLevelUseCase,
    confirmLevels: ConfirmPlayerLevelsUseCase,
    undoLevelChange: UndoLevelChangeUseCase,
    declareConvoy: DeclareConvoyUseCase,
    pasteConvoys: PasteConvoysUseCase,
    deleteConvoys: DeleteConvoyUseCase,
    clock: Clock,
    allianceTag: String,
) {
    get<Me> { page ->
        val viewer = call.viewer
        val overview = getOverview(allianceTag, viewer.playerId, page.change)
        call.respondHtml { mePage(viewer, overview, clock.now()) }
    }

    post<Me.Level> { action ->
        val viewer = call.viewer
        val parameters = call.receiveParameters()
        val kind = PlayerLevel.entries.find { it.name == action.kind }
        // A button steps the level, the field holds the one typed
        val level = (parameters[MeFields.LEVEL] ?: parameters[MeFields.TYPED])?.trim()?.toLongOrNull()
            ?.takeIf { it in 0..PlayerLevel.MAX_LEVEL.toLong() }
        val change = if (kind != null && level != null) saveLevel(viewer.playerId, kind, level, viewer.playerId) else null
        call.backTo(MeSection.LEVELS, Me(change = change?.id))
    }

    post<Me.ConfirmLevels> {
        confirmLevels(call.viewer.playerId, call.viewer.playerId)
        call.backTo(MeSection.LEVELS)
    }

    post<Me.UndoLevelChange> { action ->
        undoLevelChange(action.id, call.viewer.playerId, call.viewer.playerId)
        call.backTo(MeSection.LEVELS)
    }

    post<Me.Declare> { action ->
        val viewer = call.viewer
        declareConvoy(allianceTag, action.computationId, viewer.playerId, action.to, viewer.playerId)
        call.backTo(MeSection.SEND)
    }

    // Answered with the page itself: what was refused cannot be said after a redirect
    post<Me.Convoys> {
        val viewer = call.viewer
        val text = call.receiveParameters()[MeFields.MESSAGE].orEmpty()
        val paste = pasteConvoys(allianceTag, viewer.playerId, text, viewer.playerId)
        val overview = getOverview(allianceTag, viewer.playerId)
        val status = if (paste.nothing) HttpStatusCode.BadRequest else HttpStatusCode.OK
        call.respondHtml(status) { mePage(viewer, overview, clock.now(), PasteForm(text, paste)) }
    }

    post<Me.Convoys.Delete> {
        val parameters = call.receiveParameters()
        val ids = parameters.getAll(MeFields.ID).orEmpty().mapNotNull { it.toLongOrNull() }
        deleteConvoys(ids, call.viewer.playerId)
        call.backTo(MeSection.entries.find { it.id == parameters[MeFields.BACK] } ?: MeSection.CONVOYS)
    }
}

private suspend fun ApplicationCall.backTo(section: MeSection, page: Me = Me()) =
    respondRedirect("${application.href(page)}#${section.id}")
