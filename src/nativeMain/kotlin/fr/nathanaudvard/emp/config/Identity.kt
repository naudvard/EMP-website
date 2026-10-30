package fr.nathanaudvard.emp.config

import fr.nathanaudvard.emp.domain.exception.ForbiddenException
import fr.nathanaudvard.emp.domain.exception.UnauthorizedException
import fr.nathanaudvard.emp.domain.model.Identification
import fr.nathanaudvard.emp.domain.model.Viewer
import fr.nathanaudvard.emp.domain.usecase.IdentifyViewerUseCase
import fr.nathanaudvard.emp.service.SESSION_DURATION
import fr.nathanaudvard.emp.web.redirect
import fr.nathanaudvard.emp.web.resources.Access
import fr.nathanaudvard.emp.web.resources.AccessNotice
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.AuthenticationConfig
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.bearer
import io.ktor.server.auth.principal
import io.ktor.server.auth.session
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.request.uri
import io.ktor.server.resources.href
import io.ktor.server.routing.Route
import io.ktor.server.sessions.SessionSerializer
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.cookie
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import io.ktor.util.AttributeKey

const val SESSION_COOKIE = "emp_session"

private const val MEMBER_SESSION = "member-session"
private const val CHIEF_SESSION = "chief-session"
private const val API_TOKEN = "api-token"

// What the session turned out to be, for the challenge to say it
private val IdentificationKey = AttributeKey<Identification>("Identification")

// The member whose session the call carries, on the routes of memberAccess
val ApplicationCall.viewer: Viewer get() = checkNotNull(principal<Viewer>()) { "No member's session on ${request.uri}" }

fun ApplicationCall.requireChief(): Viewer =
    viewer.also { if (!it.chief) throw ForbiddenException("Reserved to the chiefs") }

fun ApplicationCall.requireEdit(playerId: Long): Viewer =
    viewer.also { if (!it.canEdit(playerId)) throw ForbiddenException("Only a chief acts for player $playerId") }

// Each visit sets the cookie again: it expires a year after the latest one
suspend fun ApplicationCall.identify(identifyViewer: IdentifyViewerUseCase, allianceTag: String): Identification {
    val token = sessions.get<String>() ?: return Identification.Unknown
    return identifyViewer(allianceTag, token).also { if (it is Identification.Member) sessions.set(token) }
}

suspend fun Application.configureIdentity() {
    val identify: IdentifyViewerUseCase = dependencies.resolve()
    val config: AppConfig = dependencies.resolve()
    val allianceTag = config.alliance.tag
    val apiToken = config.api.token

    install(Sessions) {
        cookie<String>(SESSION_COOKIE) {
            serializer = TokenSerializer
            cookie.path = "/"
            cookie.maxAgeInSeconds = SESSION_DURATION.inWholeSeconds
            cookie.secure = true
            cookie.httpOnly = true
            cookie.extensions["SameSite"] = "Lax"
        }
    }

    install(Authentication) {
        memberSession(MEMBER_SESSION, identify, allianceTag, accepts = { true }) { identification ->
            val notice = AccessNotice.LEFT.takeIf { identification == Identification.Left }
            redirect(application.href(Access(notice)))
        }
        memberSession(CHIEF_SESSION, identify, allianceTag, accepts = Viewer::chief) { identification ->
            if (identification is Identification.Member) throw ForbiddenException("Reserved to the chiefs")
            throw UnauthorizedException("An API token or a chief's session is required")
        }
        bearer(API_TOKEN) {
            authenticate { credential ->
                UserIdPrincipal("api").takeIf { apiToken.isNotEmpty() && credential.token == apiToken }
            }
        }
    }
}

private fun AuthenticationConfig.memberSession(
    name: String,
    identifyViewer: IdentifyViewerUseCase,
    allianceTag: String,
    accepts: (Viewer) -> Boolean,
    challenge: suspend ApplicationCall.(Identification) -> Unit,
) = session<String>(name) {
    validate {
        val identification = identify(identifyViewer, allianceTag)
        attributes.put(IdentificationKey, identification)
        (identification as? Identification.Member)?.viewer?.takeIf(accepts)
    }
    challenge { call.challenge(call.attributes.getOrNull(IdentificationKey) ?: Identification.Unknown) }
}

// The cookie holds the token as it is
private object TokenSerializer : SessionSerializer<String> {
    override fun serialize(session: String) = session

    override fun deserialize(text: String) = text
}

// The routes built in it require a member's session. SameSite=Lax keeps the cookie off posts from other sites
fun Route.memberAccess(build: Route.() -> Unit): Route = authenticate(MEMBER_SESSION, build = build)

// External calls carry the API token, those made from the pages a chief's session
fun Route.apiAccess(build: Route.() -> Unit): Route = authenticate(API_TOKEN, CHIEF_SESSION, build = build)
