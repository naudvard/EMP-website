package fr.nathanaudvard.emp.web.controllers

import fr.nathanaudvard.emp.web.pages.href
import fr.nathanaudvard.emp.web.resources.Alliance
import fr.nathanaudvard.emp.web.resources.Home
import fr.nathanaudvard.emp.web.resources.Me
import io.ktor.server.resources.get
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

// The site opens on the viewer's page. Under the member access: without a session, the access screen
fun Route.homeController() {
    get<Home> { call.respondRedirect(href(Me())) }
}

// Bookmarks of the former pages open the pages that took them over. They reveal nothing: no session is needed
fun Route.formerPagesController() {
    val pages = mapOf(
        "/my-deliveries" to href(Me()),
        "/update-levels" to href(Me()),
        "/debts" to href(Alliance()),
        "/access/links" to href(Alliance()),
    )
    pages.forEach { (former, page) -> get(former) { call.respondRedirect(page, permanent = true) } }
}
