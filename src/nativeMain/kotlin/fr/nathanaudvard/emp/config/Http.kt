package fr.nathanaudvard.emp.config

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.bodylimit.RequestBodyLimit
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import io.ktor.server.plugins.forwardedheaders.XForwardedHeaders

// The longest bodies are the game's messages pasted on the pages
const val MAX_BODY_SIZE = 1024L * 1024

fun Application.configureHttp() {
    // The site is only reached through the reverse proxy: the links sent are on the address it was reached at
    install(XForwardedHeaders)
    install(DefaultHeaders) {
        header("X-Content-Type-Options", "nosniff")
        // The access links carry their token in the path
        header("Referrer-Policy", "same-origin")
        header("X-Frame-Options", "DENY")
    }
    install(RequestBodyLimit) { bodyLimit { MAX_BODY_SIZE } }
}
