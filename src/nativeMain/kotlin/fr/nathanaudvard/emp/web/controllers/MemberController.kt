package fr.nathanaudvard.emp.web.controllers

import fr.nathanaudvard.emp.config.requireChief
import fr.nathanaudvard.emp.domain.model.MemberDetail
import fr.nathanaudvard.emp.domain.model.PlayerLevel
import fr.nathanaudvard.emp.domain.model.WageSettlement
import fr.nathanaudvard.emp.domain.service.AccessService
import fr.nathanaudvard.emp.domain.service.AllianceService
import fr.nathanaudvard.emp.domain.service.ConvoyService
import fr.nathanaudvard.emp.domain.service.PlayerService
import fr.nathanaudvard.emp.web.pages.AllianceView
import fr.nathanaudvard.emp.web.pages.CHIEF_FIELD
import fr.nathanaudvard.emp.web.pages.FILTER_FIELD
import fr.nathanaudvard.emp.web.pages.MeFields
import fr.nathanaudvard.emp.web.pages.MemberFilter
import fr.nathanaudvard.emp.web.pages.MemberPage
import fr.nathanaudvard.emp.web.pages.MemberSection
import fr.nathanaudvard.emp.web.pages.MemberView
import fr.nathanaudvard.emp.web.pages.SETTLEMENT_FIELD
import fr.nathanaudvard.emp.web.pages.accessCard
import fr.nathanaudvard.emp.web.pages.accessMessage
import fr.nathanaudvard.emp.web.pages.allianceMembers
import fr.nathanaudvard.emp.web.pages.memberHeader
import fr.nathanaudvard.emp.web.pages.memberLevels
import fr.nathanaudvard.emp.web.pages.memberPage
import fr.nathanaudvard.emp.web.pages.memberSendingColumn
import fr.nathanaudvard.emp.web.pages.settlementCard
import fr.nathanaudvard.emp.web.pages.unknownMemberPage
import fr.nathanaudvard.emp.web.redirect
import fr.nathanaudvard.emp.web.resources.Access
import fr.nathanaudvard.emp.web.resources.Alliance
import fr.nathanaudvard.emp.web.resources.MemberNotice
import fr.nathanaudvard.emp.web.respondSections
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.html.respondHtml
import io.ktor.server.htmx.hx
import io.ktor.server.request.accept
import io.ktor.server.request.receiveParameters
import io.ktor.server.resources.get
import io.ktor.server.resources.href
import io.ktor.server.resources.post
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import kotlinx.html.DIV
import kotlin.time.Clock

// A member's page, for the chiefs only: everything posted here is done in the member's name, the chief being recorded
// as its author, and gets back the sections it changed
fun Route.memberController(
    alliance: AllianceService,
    players: PlayerService,
    convoys: ConvoyService,
    access: AccessService,
    clock: Clock,
    publicUrl: String,
    allianceTag: String,
) {
    suspend fun ApplicationCall.loadPage(pseudo: String, change: Long? = null): MemberPage? =
        alliance.member(allianceTag, pseudo, change)?.let { MemberPage(requireChief(), it, clock.now()) }

    // An unknown pseudo is said on the page: StatusPages would replace a 404 page with its JSON error
    suspend fun ApplicationCall.respondMember(pseudo: String, view: MemberView = MemberView()) {
        val chief = requireChief()
        val page = loadPage(pseudo) ?: return respondHtml { unknownMemberPage(chief, pseudo) }
        respondHtml { memberPage(page, view) }
    }

    // The member a post acts for; back to the list when no member has the pseudo any more
    suspend fun ApplicationCall.target(pseudo: String): MemberDetail? {
        requireChief()
        return alliance.member(allianceTag, pseudo) ?: run {
            redirect(application.href(Alliance()))
            null
        }
    }

    suspend fun ApplicationCall.respondPage(detail: MemberDetail, change: Long? = null, sections: DIV.(MemberPage) -> Unit) {
        val content = loadPage(detail.member.member.pseudo, change) ?: return redirect(application.href(Alliance()))
        respondSections { sections(content) }
    }

    get<Alliance.Member> { page ->
        call.respondMember(page.pseudo, MemberView(page.notice))
    }

    hx {
        // The change just saved is shown with the way to undo it
        post<Alliance.Member.Level> { action ->
            val detail = call.target(action.parent.pseudo) ?: return@post
            val parameters = call.receiveParameters()
            val kind = PlayerLevel.entries.find { it.name == action.kind }
            val level = (parameters[MeFields.LEVEL] ?: parameters[MeFields.TYPED])?.trim()?.toLongOrNull()
                ?.takeIf { it in 0..PlayerLevel.MAX_LEVEL.toLong() }
            val playerId = detail.member.member.id
            val change = if (kind != null && level != null) players.saveLevel(playerId, kind, level, call.requireChief().playerId) else null
            call.respondPage(detail, change?.id) { memberLevels(it) }
        }

        post<Alliance.Member.ConfirmLevels> { action ->
            val detail = call.target(action.parent.pseudo) ?: return@post
            players.confirmLevels(detail.member.member.id, call.requireChief().playerId)
            call.respondPage(detail) { memberLevels(it) }
        }

        post<Alliance.Member.UndoLevelChange> { action ->
            val detail = call.target(action.parent.pseudo) ?: return@post
            players.undoLevelChange(action.id, detail.member.member.id, call.requireChief().playerId)
            call.respondPage(detail) { memberLevels(it) }
        }

        post<Alliance.Member.Declare> { action ->
            val detail = call.target(action.parent.pseudo) ?: return@post
            convoys.declare(allianceTag, action.computationId, detail.member.member.id, action.to, call.requireChief().playerId)
            call.respondPage(detail) { memberSendingColumn(it) }
        }

        post<Alliance.Member.Convoys.Delete> { action ->
            val detail = call.target(action.parent.parent.pseudo) ?: return@post
            val ids = call.receiveParameters().getAll(MeFields.ID).orEmpty().mapNotNull { it.toLongOrNull() }
            convoys.delete(ids, detail.member.member.id)
            call.respondPage(detail) { memberSendingColumn(it) }
        }

        // From the list, the list as it was filtered
        post<Alliance.Member.Settlement> { action ->
            val detail = call.target(action.parent.pseudo) ?: return@post
            val parameters = call.receiveParameters()
            val member = detail.member.member
            val settlement = WageSettlement.entries.find { it.name == parameters[SETTLEMENT_FIELD] }
            val previous = settlement?.let { alliance.saveSettlement(allianceTag, member.id, it, call.requireChief().playerId) }
            val filter = parameters[FILTER_FIELD]
            if (filter != null) {
                val view = AllianceView(MemberFilter.of(filter), settled = member.id.takeIf { previous != null }, previous = previous)
                val overview = alliance.overview(allianceTag)
                val chief = call.requireChief()
                return@post call.respondSections { allianceMembers(chief, overview, clock.now(), view) }
            }
            val notice = MemberNotice.SETTLED.takeIf { previous != null }
            call.respondPage(detail) { settlementCard(it, MemberView(notice, previous)) }
        }

        // A chief who gives up their own role leaves the page, now reserved to the others
        post<Alliance.Member.Chief> { action ->
            val detail = call.target(action.parent.pseudo) ?: return@post
            val chief = call.receiveParameters()[CHIEF_FIELD] == "true"
            val member = detail.member.member
            val notice = when {
                !access.setChief(allianceTag, member.id, chief) -> MemberNotice.LAST_CHIEF
                chief -> MemberNotice.CHIEF
                member.id == call.requireChief().playerId -> return@post call.redirect(call.application.href(Alliance()))
                else -> MemberNotice.NOT_CHIEF
            }
            call.respondPage(detail) {
                accessCard(it, MemberView(notice))
                memberHeader(it)
            }
        }
    }

    post<Alliance.Member.Message> { action ->
        val detail = call.target(action.parent.pseudo) ?: return@post
        val (member, token) = access.sendLink(allianceTag, detail.member.member.id, call.requireChief().playerId)
            ?: return@post call.respondRedirect(call.application.href(Alliance()))
        val message = accessMessage(call.publicUrl(publicUrl, call.application.href(Access.Link(token = token))))
        // The copy button's script asks for the text alone
        if (call.request.accept()?.contains(ContentType.Text.Plain.toString()) == true) {
            call.respondText(message)
        } else {
            call.respondMember(member.pseudo, MemberView(copied = message))
        }
    }

    post<Alliance.Member.Replace> { action ->
        val detail = call.target(action.parent.pseudo) ?: return@post
        access.replaceLink(detail.member.member.id)
        val page = Alliance.Member(pseudo = detail.member.member.pseudo, notice = MemberNotice.REPLACED)
        call.respondRedirect("${call.application.href(page)}#${MemberSection.ACCESS.id}")
    }
}
