package fr.nathanaudvard.emp.web.controllers

import fr.nathanaudvard.emp.web.assets.script
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
        val (content, type) = stylesheets.find { it.name == asset.name }?.let { it.content to ContentType.Text.CSS }
            ?: script.takeIf { it.name == asset.name }?.let { it.content to ContentType.Text.JavaScript }
            ?: return@get call.respond(HttpStatusCode.NotFound)
        call.response.header(HttpHeaders.CacheControl, "public, max-age=31536000, immutable")
        call.respondText(content, type)
    }
}
