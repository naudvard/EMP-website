package fr.nathanaudvard.emp.web.controllers

import fr.nathanaudvard.emp.config.identify
import fr.nathanaudvard.emp.config.requireChief
import fr.nathanaudvard.emp.config.requireSameOrigin
import fr.nathanaudvard.emp.config.setSessionCookie
import fr.nathanaudvard.emp.config.viewer
import fr.nathanaudvard.emp.domain.model.Identification
import fr.nathanaudvard.emp.domain.model.SignIn
import fr.nathanaudvard.emp.domain.usecase.CreateDeviceCodeUseCase
import fr.nathanaudvard.emp.domain.usecase.GetChiefsUseCase
import fr.nathanaudvard.emp.domain.usecase.GetMemberAccessesUseCase
import fr.nathanaudvard.emp.domain.usecase.IdentifyViewerUseCase
import fr.nathanaudvard.emp.domain.usecase.OpenAccessLinkUseCase
import fr.nathanaudvard.emp.domain.usecase.ReplaceAccessLinkUseCase
import fr.nathanaudvard.emp.domain.usecase.SendAccessLinkUseCase
import fr.nathanaudvard.emp.domain.usecase.SignInWithCodeUseCase
import fr.nathanaudvard.emp.web.pages.DEVICE_CODE_FIELD
import fr.nathanaudvard.emp.web.pages.accessLinksPage
import fr.nathanaudvard.emp.web.pages.accessMessage
import fr.nathanaudvard.emp.web.pages.accessPage
import fr.nathanaudvard.emp.web.pages.devicePage
import fr.nathanaudvard.emp.web.pages.message
import fr.nathanaudvard.emp.web.resources.Access
import fr.nathanaudvard.emp.web.resources.Home
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.html.respondHtml
import io.ktor.server.plugins.origin
import io.ktor.server.request.accept
import io.ktor.server.request.receiveParameters
import io.ktor.server.resources.get
import io.ktor.server.resources.href
import io.ktor.server.resources.post
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.util.url
import kotlin.time.Clock

// The pages a browser without a session reaches
fun Route.accessController(
    identify: IdentifyViewerUseCase,
    openLink: OpenAccessLinkUseCase,
    signInWithCode: SignInWithCodeUseCase,
    getChiefs: GetChiefsUseCase,
    allianceTag: String,
) {
    suspend fun ApplicationCall.respondAccess(status: HttpStatusCode, notice: String? = null, code: String = "", codeError: String? = null) {
        val chiefs = getChiefs(allianceTag).map { it.pseudo }
        respondHtml(status) { accessPage(allianceTag, chiefs, notice, code, codeError) }
    }

    suspend fun ApplicationCall.signedIn(signIn: SignIn.Opened) {
        setSessionCookie(signIn.sessionToken)
        respondRedirect(application.href(Home()))
    }

    get<Access> { page ->
        // A browser already recognised goes straight to the pages
        if (page.notice == null && call.identify(identify, allianceTag) is Identification.Member) {
            return@get call.respondRedirect(call.application.href(Home()))
        }
        call.respondAccess(HttpStatusCode.OK, page.notice?.message(allianceTag))
    }

    get<Access.Link> { link ->
        when (val signIn = openLink(allianceTag, link.token)) {
            is SignIn.Opened -> call.signedIn(signIn)
            else -> call.respondAccess(HttpStatusCode.Forbidden, signIn.message(allianceTag))
        }
    }

    post<Access.Code> {
        call.requireSameOrigin()
        val code = call.receiveParameters()[DEVICE_CODE_FIELD].orEmpty().trim()
        when (val signIn = signInWithCode(allianceTag, code, call.request.origin.remoteHost)) {
            is SignIn.Opened -> call.signedIn(signIn)
            SignIn.TooManyAttempts -> call.respondAccess(HttpStatusCode.TooManyRequests, code = code, codeError = signIn.message(allianceTag))
            SignIn.Left -> call.respondAccess(HttpStatusCode.Forbidden, signIn.message(allianceTag))
            else -> call.respondAccess(HttpStatusCode.BadRequest, code = code, codeError = signIn.message(allianceTag))
        }
    }
}

fun Route.deviceController(createDeviceCode: CreateDeviceCodeUseCase) {
    post<Access.Device> {
        val code = createDeviceCode(call.viewer)
        call.respondHtml { devicePage(code) }
    }
}

// Reserved to the chiefs
fun Route.accessLinksController(
    getAccesses: GetMemberAccessesUseCase,
    sendLink: SendAccessLinkUseCase,
    replaceLink: ReplaceAccessLinkUseCase,
    clock: Clock,
    publicUrl: String,
    allianceTag: String,
) {
    get<Access.Links> { page ->
        call.requireChief()
        val accesses = getAccesses(allianceTag)
        val replaced = accesses.find { it.member.id == page.replaced }?.member?.pseudo
        call.respondHtml { accessLinksPage(accesses, clock.now(), replaced) }
    }

    post<Access.Links.Message> { action ->
        val chief = call.requireChief()
        val (member, token) = sendLink(allianceTag, action.playerId, chief.playerId)
            ?: return@post call.respondRedirect(call.application.href(Access.Links()))
        val message = accessMessage(call.accessUrl(publicUrl, token))
        // The copy button's script asks for the text alone
        if (call.request.accept()?.contains(ContentType.Text.Plain.toString()) == true) {
            call.respondText(message)
        } else {
            val accesses = getAccesses(allianceTag)
            call.respondHtml { accessLinksPage(accesses, clock.now(), copied = member to message) }
        }
    }

    post<Access.Links.Replace> { action ->
        call.requireChief()
        replaceLink(action.playerId)
        call.respondRedirect(call.application.href(Access.Links(replaced = action.playerId)))
    }
}

// On the address the members reach the site at, else on the one the chief does
private fun ApplicationCall.accessUrl(publicUrl: String, token: String): String {
    val base = publicUrl.ifEmpty {
        url {
            pathSegments = emptyList()
            parameters.clear()
        }
    }
    return base.trimEnd('/') + application.href(Access.Link(token = token))
}
