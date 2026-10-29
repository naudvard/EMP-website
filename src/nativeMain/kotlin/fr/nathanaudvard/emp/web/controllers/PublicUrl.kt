package fr.nathanaudvard.emp.web.controllers

import io.ktor.server.application.ApplicationCall
import io.ktor.server.util.url

// A page on the address the members reach the site at, else on the one of the request, for the texts sent to them
fun ApplicationCall.publicUrl(publicUrl: String, path: String): String {
    val base = publicUrl.ifEmpty {
        url {
            pathSegments = emptyList()
            parameters.clear()
        }
    }
    return base.trimEnd('/') + path
}
