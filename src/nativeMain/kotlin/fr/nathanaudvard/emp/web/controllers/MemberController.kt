package fr.nathanaudvard.emp.web.controllers

import fr.nathanaudvard.emp.config.requireChief
import fr.nathanaudvard.emp.domain.model.MemberDetail
import fr.nathanaudvard.emp.domain.model.PlayerLevel
import fr.nathanaudvard.emp.domain.model.WageSettlement
import fr.nathanaudvard.emp.domain.usecase.ConfirmPlayerLevelsUseCase
import fr.nathanaudvard.emp.domain.usecase.DeclareConvoyUseCase
import fr.nathanaudvard.emp.domain.usecase.DeleteConvoyUseCase
import fr.nathanaudvard.emp.domain.usecase.GetMemberUseCase
import fr.nathanaudvard.emp.domain.usecase.PasteConvoysUseCase
import fr.nathanaudvard.emp.domain.usecase.ReplaceAccessLinkUseCase
import fr.nathanaudvard.emp.domain.usecase.SavePlayerLevelUseCase
import fr.nathanaudvard.emp.domain.usecase.SaveWageSettlementUseCase
import fr.nathanaudvard.emp.domain.usecase.SendAccessLinkUseCase
import fr.nathanaudvard.emp.domain.usecase.SetChiefUseCase
import fr.nathanaudvard.emp.domain.usecase.UndoLevelChangeUseCase
import fr.nathanaudvard.emp.web.pages.CHIEF_FIELD
import fr.nathanaudvard.emp.web.pages.FILTER_FIELD
import fr.nathanaudvard.emp.web.pages.MEMBERS_SECTION
import fr.nathanaudvard.emp.web.pages.MeFields
import fr.nathanaudvard.emp.web.pages.MeSection
import fr.nathanaudvard.emp.web.pages.MemberFilter
import fr.nathanaudvard.emp.web.pages.MemberSection
import fr.nathanaudvard.emp.web.pages.MemberView
import fr.nathanaudvard.emp.web.pages.PSEUDO_FIELD
import fr.nathanaudvard.emp.web.pages.PasteForm
import fr.nathanaudvard.emp.web.pages.SETTLEMENT_FIELD
import fr.nathanaudvard.emp.web.pages.accessMessage
import fr.nathanaudvard.emp.web.pages.memberPage
import fr.nathanaudvard.emp.web.pages.unknownMemberPage
import fr.nathanaudvard.emp.web.resources.Access
import fr.nathanaudvard.emp.web.resources.Alliance
import fr.nathanaudvard.emp.web.resources.MemberNotice
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.html.respondHtml
import io.ktor.server.request.accept
import io.ktor.server.request.receiveParameters
import io.ktor.server.resources.get
import io.ktor.server.resources.href
import io.ktor.server.resources.post
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import kotlin.time.Clock

// A member's page, for the chiefs only: everything posted here is done in the member's name, the chief being recorded
// as its author. Without JavaScript each post comes back to the page, at the section it changed
fun Route.memberController(
    getMember: GetMemberUseCase,
    saveLevel: SavePlayerLevelUseCase,
    confirmLevels: ConfirmPlayerLevelsUseCase,
    undoLevelChange: UndoLevelChangeUseCase,
    declareConvoy: DeclareConvoyUseCase,
    pasteConvoys: PasteConvoysUseCase,
    deleteConvoys: DeleteConvoyUseCase,
    saveSettlement: SaveWageSettlementUseCase,
    setChief: SetChiefUseCase,
    sendLink: SendAccessLinkUseCase,
    replaceLink: ReplaceAccessLinkUseCase,
    clock: Clock,
    publicUrl: String,
    allianceTag: String,
) {
    // An unknown pseudo is said on the page: StatusPages would replace a 404 page with its JSON error
    suspend fun ApplicationCall.respondMember(pseudo: String, view: MemberView = MemberView(), change: Long? = null, status: HttpStatusCode = HttpStatusCode.OK) {
        val chief = requireChief()
        val detail = getMember(allianceTag, pseudo, change)
            ?: return respondHtml { unknownMemberPage(chief, pseudo) }
        respondHtml(status) { memberPage(chief, detail, clock.now(), view) }
    }

    // The member a post acts for; back to the list when no member has the pseudo any more
    suspend fun ApplicationCall.target(pseudo: String): MemberDetail? {
        requireChief()
        return getMember(allianceTag, pseudo) ?: run {
            respondRedirect(application.href(Alliance()))
            null
        }
    }

    suspend fun ApplicationCall.backTo(detail: MemberDetail, section: String, page: Alliance.Member = Alliance.Member(pseudo = detail.member.member.pseudo)) =
        respondRedirect("${application.href(page)}#$section")

    get<Alliance.Member> { page ->
        call.respondMember(page.pseudo, MemberView(page.notice, page.previous), page.change)
    }

    post<Alliance.Member.Level> { action ->
        val detail = call.target(action.parent.pseudo) ?: return@post
        val parameters = call.receiveParameters()
        val kind = PlayerLevel.entries.find { it.name == action.kind }
        val level = (parameters[MeFields.LEVEL] ?: parameters[MeFields.TYPED])?.trim()?.toLongOrNull()
            ?.takeIf { it in 0..PlayerLevel.MAX_LEVEL.toLong() }
        val playerId = detail.member.member.id
        val change = if (kind != null && level != null) saveLevel(playerId, kind, level, call.requireChief().playerId) else null
        call.backTo(detail, MeSection.LEVELS.id, Alliance.Member(pseudo = detail.member.member.pseudo, change = change?.id))
    }

    post<Alliance.Member.ConfirmLevels> { action ->
        val detail = call.target(action.parent.pseudo) ?: return@post
        confirmLevels(detail.member.member.id, call.requireChief().playerId)
        call.backTo(detail, MeSection.LEVELS.id)
    }

    post<Alliance.Member.UndoLevelChange> { action ->
        val detail = call.target(action.parent.pseudo) ?: return@post
        undoLevelChange(action.id, detail.member.member.id, call.requireChief().playerId)
        call.backTo(detail, MeSection.LEVELS.id)
    }

    post<Alliance.Member.Declare> { action ->
        val detail = call.target(action.parent.pseudo) ?: return@post
        declareConvoy(allianceTag, action.computationId, detail.member.member.id, action.to, call.requireChief().playerId)
        call.backTo(detail, MeSection.SEND.id)
    }

    // Answered with the page itself: what was refused cannot be said after a redirect
    post<Alliance.Member.Convoys> { action ->
        val detail = call.target(action.parent.pseudo) ?: return@post
        val text = call.receiveParameters()[MeFields.MESSAGE].orEmpty()
        val paste = pasteConvoys(allianceTag, detail.member.member.id, text, call.requireChief().playerId)
        val status = if (paste.nothing) HttpStatusCode.BadRequest else HttpStatusCode.OK
        call.respondMember(detail.member.member.pseudo, MemberView(paste = PasteForm(text, paste)), status = status)
    }

    post<Alliance.Member.Convoys.Delete> { action ->
        val detail = call.target(action.parent.parent.pseudo) ?: return@post
        val parameters = call.receiveParameters()
        val ids = parameters.getAll(MeFields.ID).orEmpty().mapNotNull { it.toLongOrNull() }
        deleteConvoys(ids, detail.member.member.id)
        call.backTo(detail, MeSection.entries.find { it.id == parameters[MeFields.BACK] }?.id ?: MeSection.CONVOYS.id)
    }

    // From the list, back to the list as it was filtered
    post<Alliance.Member.Settlement> { action ->
        val detail = call.target(action.parent.pseudo) ?: return@post
        val parameters = call.receiveParameters()
        val member = detail.member.member
        val settlement = WageSettlement.entries.find { it.name == parameters[SETTLEMENT_FIELD] }
        val previous = settlement?.let { saveSettlement(allianceTag, member.id, it, call.requireChief().playerId) }
        val filter = parameters[FILTER_FIELD]
        if (filter != null) {
            val list = Alliance(
                filter = MemberFilter.of(filter).takeIf { it != MemberFilter.ALL }?.key,
                pseudo = parameters[PSEUDO_FIELD]?.takeIf { it.isNotBlank() },
                settled = member.id.takeIf { previous != null },
                previous = previous,
            )
            return@post call.respondRedirect("${call.application.href(list)}#$MEMBERS_SECTION")
        }
        val notice = MemberNotice.SETTLED.takeIf { previous != null }
        call.backTo(detail, MemberSection.SETTLEMENT.id, Alliance.Member(pseudo = member.pseudo, notice = notice, previous = previous))
    }

    // A chief who gives up their own role leaves the page, now reserved to the others
    post<Alliance.Member.Chief> { action ->
        val detail = call.target(action.parent.pseudo) ?: return@post
        val chief = call.receiveParameters()[CHIEF_FIELD] == "true"
        val member = detail.member.member
        val notice = when {
            !setChief(allianceTag, member.id, chief) -> MemberNotice.LAST_CHIEF
            chief -> MemberNotice.CHIEF
            member.id == call.requireChief().playerId -> return@post call.respondRedirect(call.application.href(Alliance()))
            else -> MemberNotice.NOT_CHIEF
        }
        call.backTo(detail, MemberSection.ACCESS.id, Alliance.Member(pseudo = member.pseudo, notice = notice))
    }

    post<Alliance.Member.Message> { action ->
        val detail = call.target(action.parent.pseudo) ?: return@post
        val (member, token) = sendLink(allianceTag, detail.member.member.id, call.requireChief().playerId)
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
        replaceLink(detail.member.member.id)
        call.backTo(detail, MemberSection.ACCESS.id, Alliance.Member(pseudo = detail.member.member.pseudo, notice = MemberNotice.REPLACED))
    }
}
