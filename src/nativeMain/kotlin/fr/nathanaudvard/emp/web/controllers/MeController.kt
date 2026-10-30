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
import fr.nathanaudvard.emp.web.pages.MyPage
import fr.nathanaudvard.emp.web.pages.PasteForm
import fr.nathanaudvard.emp.web.pages.mePage
import fr.nathanaudvard.emp.web.pages.myLevels
import fr.nathanaudvard.emp.web.pages.sendingColumn
import fr.nathanaudvard.emp.web.resources.Me
import fr.nathanaudvard.emp.web.respondSections
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.html.respondHtml
import io.ktor.server.htmx.hx
import io.ktor.server.request.receiveParameters
import io.ktor.server.resources.get
import io.ktor.server.resources.post
import io.ktor.server.routing.Route
import kotlinx.html.DIV
import kotlin.time.Clock

// The viewer's own page: everything posted here acts for the viewer, and gets back the sections it changed
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
    suspend fun ApplicationCall.myPage(change: Long? = null) =
        MyPage(viewer, getOverview(allianceTag, viewer.playerId, change), clock.now())

    suspend fun ApplicationCall.respondPage(change: Long? = null, sections: DIV.(MyPage) -> Unit) {
        val content = myPage(change)
        respondSections { sections(content) }
    }

    get<Me> {
        val content = call.myPage()
        call.respondHtml { mePage(content) }
    }

    hx {
        // The change just saved is shown with the way to undo it
        post<Me.Level> { action ->
            val viewer = call.viewer
            val parameters = call.receiveParameters()
            val kind = PlayerLevel.entries.find { it.name == action.kind }
            // A button steps the level, the field holds the one typed
            val level = (parameters[MeFields.LEVEL] ?: parameters[MeFields.TYPED])?.trim()?.toLongOrNull()
                ?.takeIf { it in 0..PlayerLevel.MAX_LEVEL.toLong() }
            val change = if (kind != null && level != null) saveLevel(viewer.playerId, kind, level, viewer.playerId) else null
            call.respondPage(change?.id) { myLevels(it) }
        }

        post<Me.ConfirmLevels> {
            confirmLevels(call.viewer.playerId, call.viewer.playerId)
            call.respondPage { myLevels(it) }
        }

        post<Me.UndoLevelChange> { action ->
            undoLevelChange(action.id, call.viewer.playerId, call.viewer.playerId)
            call.respondPage { myLevels(it) }
        }

        post<Me.Declare> { action ->
            val viewer = call.viewer
            declareConvoy(allianceTag, action.computationId, viewer.playerId, action.to, viewer.playerId)
            call.respondPage { sendingColumn(it) }
        }

        post<Me.Convoys> {
            val viewer = call.viewer
            val text = call.receiveParameters()[MeFields.MESSAGE].orEmpty()
            val paste = PasteForm(text, pasteConvoys(allianceTag, viewer.playerId, text, viewer.playerId))
            val page = call.myPage()
            val status = if (paste.result.nothing) HttpStatusCode.BadRequest else HttpStatusCode.OK
            call.respondSections(status) { sendingColumn(page, paste) }
        }

        post<Me.Convoys.Delete> {
            val ids = call.receiveParameters().getAll(MeFields.ID).orEmpty().mapNotNull { it.toLongOrNull() }
            deleteConvoys(ids, call.viewer.playerId)
            call.respondPage { sendingColumn(it) }
        }
    }
}
