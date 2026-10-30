package fr.nathanaudvard.emp.web.controllers

import fr.nathanaudvard.emp.config.identify
import fr.nathanaudvard.emp.config.viewer
import fr.nathanaudvard.emp.domain.model.Identification
import fr.nathanaudvard.emp.domain.model.SignIn
import fr.nathanaudvard.emp.domain.service.AccessService
import fr.nathanaudvard.emp.web.pages.DEVICE_CODE_FIELD
import fr.nathanaudvard.emp.web.pages.accessPage
import fr.nathanaudvard.emp.web.pages.deviceBlock
import fr.nathanaudvard.emp.web.pages.message
import fr.nathanaudvard.emp.web.resources.Access
import fr.nathanaudvard.emp.web.resources.Me
import fr.nathanaudvard.emp.web.respondSections
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.html.respondHtml
import io.ktor.server.htmx.hx
import io.ktor.server.request.receiveParameters
import io.ktor.server.resources.get
import io.ktor.server.resources.href
import io.ktor.server.resources.post
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set

// The pages a browser without a session reaches
fun Route.accessController(
    access: AccessService,
    allianceTag: String,
) {
    suspend fun ApplicationCall.respondAccess(status: HttpStatusCode, notice: String? = null, code: String = "", codeError: String? = null) {
        val chiefs = access.chiefs(allianceTag).map { it.pseudo }
        respondHtml(status) { accessPage(allianceTag, chiefs, notice, code, codeError) }
    }

    suspend fun ApplicationCall.signedIn(signIn: SignIn.Opened) {
        sessions.set(signIn.sessionToken)
        respondRedirect(application.href(Me()))
    }

    get<Access> { page ->
        // A browser already recognised goes straight to the pages
        if (page.notice == null && call.identify(access, allianceTag) is Identification.Member) {
            return@get call.respondRedirect(call.application.href(Me()))
        }
        call.respondAccess(HttpStatusCode.OK, page.notice?.message(allianceTag))
    }

    get<Access.Link> { link ->
        when (val signIn = access.openLink(allianceTag, link.token)) {
            is SignIn.Opened -> call.signedIn(signIn)
            // The message opened again on the browser it signed in
            SignIn.UsedLink if call.identify(access, allianceTag) is Identification.Member ->
                call.respondRedirect(call.application.href(Me()))
            else -> call.respondAccess(HttpStatusCode.Forbidden, signIn.message(allianceTag))
        }
    }

    post<Access.Code> {
        val code = call.receiveParameters()[DEVICE_CODE_FIELD].orEmpty().trim()
        when (val signIn = access.signInWithCode(allianceTag, code)) {
            is SignIn.Opened -> call.signedIn(signIn)
            SignIn.Left -> call.respondAccess(HttpStatusCode.Forbidden, signIn.message(allianceTag))
            else -> call.respondAccess(HttpStatusCode.BadRequest, code = code, codeError = signIn.message(allianceTag))
        }
    }
}

// The code is shown on the viewer's page, in place of the button
fun Route.deviceController(access: AccessService) {
    hx {
        post<Access.Device> {
            val code = access.createDeviceCode(call.viewer)
            call.respondSections { deviceBlock(code) }
        }
    }
}
