package fr.nathanaudvard.emp.config

import fr.nathanaudvard.emp.domain.exception.ForbiddenException
import fr.nathanaudvard.emp.domain.exception.MemberLeftException
import fr.nathanaudvard.emp.domain.exception.SignInRequiredException
import fr.nathanaudvard.emp.domain.exception.UnauthorizedException
import fr.nathanaudvard.emp.domain.model.Identification
import fr.nathanaudvard.emp.domain.model.Viewer
import fr.nathanaudvard.emp.domain.usecase.IdentifyViewerUseCase
import fr.nathanaudvard.emp.service.SESSION_DURATION
import fr.nathanaudvard.emp.service.secureEquals
import io.ktor.http.Cookie
import io.ktor.http.CookieEncoding
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.request.host
import io.ktor.server.request.httpMethod
import io.ktor.server.routing.Route
import io.ktor.server.routing.RouteSelector
import io.ktor.server.routing.RouteSelectorEvaluation
import io.ktor.server.routing.RoutingResolveContext
import io.ktor.util.AttributeKey

const val SESSION_COOKIE = "emp_session"

private val ViewerKey = AttributeKey<Viewer>("Viewer")

// The member whose session the call carries, on the routes of memberAccess
val ApplicationCall.viewer: Viewer get() = attributes[ViewerKey]

fun ApplicationCall.requireChief(): Viewer =
    viewer.also { if (!it.chief) throw ForbiddenException("Reserved to the chiefs") }

fun ApplicationCall.requireEdit(playerId: Long): Viewer =
    viewer.also { if (!it.canEdit(playerId)) throw ForbiddenException("Only a chief acts for player $playerId") }

// A year after the latest visit: the session is renewed with it
fun ApplicationCall.setSessionCookie(token: String) = response.cookies.append(
    Cookie(
        SESSION_COOKIE,
        token,
        CookieEncoding.RAW,
        maxAge = SESSION_DURATION.inWholeSeconds.toInt(),
        path = "/",
        secure = true,
        httpOnly = true,
        extensions = mapOf("SameSite" to "Lax"),
    ),
)

suspend fun ApplicationCall.identify(identifyViewer: IdentifyViewerUseCase, allianceTag: String): Identification {
    val token = request.cookies[SESSION_COOKIE, CookieEncoding.RAW] ?: return Identification.Unknown
    return identifyViewer(allianceTag, token).also { if (it is Identification.Member) setSessionCookie(token) }
}

// SameSite=Lax keeps the cookie off the posts of other sites; a post that says it comes from one is refused as well
fun ApplicationCall.requireSameOrigin() {
    if (request.httpMethod == HttpMethod.Get || request.httpMethod == HttpMethod.Head) return
    val origin = request.headers[HttpHeaders.Origin] ?: return
    val host = origin.substringAfter("://", "").substringBefore('/').substringBefore(':')
    if (!host.equals(request.host(), ignoreCase = true)) throw ForbiddenException("Request from another site: $origin")
}

class AccessConfig {
    lateinit var identify: IdentifyViewerUseCase
    lateinit var allianceTag: String
    var apiToken: String = ""
}

private val MemberAccess = createRouteScopedPlugin("MemberAccess", ::AccessConfig) {
    val identify = pluginConfig.identify
    val allianceTag = pluginConfig.allianceTag
    onCall { call ->
        call.requireSameOrigin()
        when (val identification = call.identify(identify, allianceTag)) {
            is Identification.Member -> call.attributes.put(ViewerKey, identification.viewer)
            Identification.Left -> throw MemberLeftException()
            Identification.Unknown -> throw SignInRequiredException()
        }
    }
}

// External calls carry the API token, those made from the pages a chief's session
private val ApiAccess = createRouteScopedPlugin("ApiAccess", ::AccessConfig) {
    val identify = pluginConfig.identify
    val allianceTag = pluginConfig.allianceTag
    val apiToken = pluginConfig.apiToken
    onCall { call ->
        val bearer = call.request.headers[HttpHeaders.Authorization]?.removePrefix("Bearer ")
        if (apiToken.isNotEmpty() && bearer != null && secureEquals(bearer, apiToken)) return@onCall
        call.requireSameOrigin()
        val identification = call.identify(identify, allianceTag)
        if (identification !is Identification.Member) throw UnauthorizedException("An API token or a chief's session is required")
        if (!identification.viewer.chief) throw ForbiddenException("Reserved to the chiefs")
    }
}

// The routes built in it require a member's session
fun Route.memberAccess(identify: IdentifyViewerUseCase, allianceTag: String, build: Route.() -> Unit): Route =
    createChild(AccessSelector("member")).apply {
        install(MemberAccess) {
            this.identify = identify
            this.allianceTag = allianceTag
        }
        build()
    }

fun Route.apiAccess(identify: IdentifyViewerUseCase, allianceTag: String, apiToken: String, build: Route.() -> Unit): Route =
    createChild(AccessSelector("api")).apply {
        install(ApiAccess) {
            this.identify = identify
            this.allianceTag = allianceTag
            this.apiToken = apiToken
        }
        build()
    }

// Matches every call: only the plugin installed under it matters
private class AccessSelector(private val name: String) : RouteSelector() {
    override suspend fun evaluate(context: RoutingResolveContext, segmentIndex: Int) = RouteSelectorEvaluation.Transparent

    override fun toString() = "($name access)"
}
