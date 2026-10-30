package fr.nathanaudvard.emp.web

import io.ktor.htmx.HxRequestHeaders
import io.ktor.htmx.HxResponseHeaders
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.withCharset
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.utils.io.charsets.Charsets
import kotlinx.html.DIV
import kotlinx.html.div
import kotlinx.html.stream.createHTML

// htmx would follow a redirect and find nothing to swap in the page it lands on: it is told to load that page instead.
// A page, or a form htmx does not post, is redirected
suspend fun ApplicationCall.redirect(url: String) {
    if (request.headers[HxRequestHeaders.Request] != "true") return respondRedirect(url)
    response.header(HxResponseHeaders.Redirect, url)
    respond(HttpStatusCode.NoContent)
}

// The sections a post changed, which htmx finds by their ids
suspend fun ApplicationCall.respondSections(status: HttpStatusCode = HttpStatusCode.OK, sections: DIV.() -> Unit) =
    respondText(createHTML(prettyPrint = false).div(block = sections), ContentType.Text.Html.withCharset(Charsets.UTF_8), status)
