package fr.nathanaudvard.emp.web.controllers

import fr.nathanaudvard.emp.web.assets.stylesheets
import fr.nathanaudvard.emp.web.resources.Assets
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.resources.get
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route

fun Route.assetsController() {
    get<Assets> { asset ->
        val sheet = stylesheets.find { it.name == asset.name } ?: return@get call.respond(HttpStatusCode.NotFound)
        call.response.header(HttpHeaders.CacheControl, "public, max-age=31536000, immutable")
        call.respondText(sheet.content, ContentType.Text.CSS)
    }
}
