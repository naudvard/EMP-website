package fr.nathanaudvard.emp.config

import fr.nathanaudvard.emp.api.dto.ErrorResponse
import fr.nathanaudvard.emp.domain.exception.AllianceNotFoundException
import fr.nathanaudvard.emp.domain.exception.ExportNotFoundException
import fr.nathanaudvard.emp.domain.exception.ExportUnavailableException
import fr.nathanaudvard.emp.domain.exception.InvalidVersionException
import fr.nathanaudvard.emp.domain.exception.LevelsTooHighException
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.engine.defaultExceptionStatusCode
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.httpMethod
import io.ktor.server.request.uri
import io.ktor.server.response.respond

fun Application.configureStatusPages() {
    install(StatusPages) {
        // A single handler: on native, StatusPages picks an arbitrary handler when no exact exception class matches
        exception<Throwable> { call, cause ->
            val status = when (cause) {
                is InvalidVersionException -> HttpStatusCode.BadRequest
                is ExportNotFoundException, is AllianceNotFoundException -> HttpStatusCode.NotFound
                is ExportUnavailableException -> HttpStatusCode.BadGateway
                is LevelsTooHighException -> HttpStatusCode.UnprocessableEntity
                else -> defaultExceptionStatusCode(cause) ?: HttpStatusCode.InternalServerError
            }
            val message = cause.message.orEmpty()
            if (status.value >= 500) {
                call.application.log.error(message, cause)
            } else {
                call.application.log.info(message)
            }
            val exposed = status.value < 500 || status == HttpStatusCode.BadGateway
            call.respond(
                status,
                ErrorResponse(status, if (exposed) message else status.description)
            )
        }
        status(HttpStatusCode.NotFound, HttpStatusCode.MethodNotAllowed) { call, status ->
            call.respond(
                status,
                ErrorResponse(
                    status,
                    "Route ${call.request.httpMethod.value}:${call.request.uri} not found"
                )
            )
        }
    }
}
