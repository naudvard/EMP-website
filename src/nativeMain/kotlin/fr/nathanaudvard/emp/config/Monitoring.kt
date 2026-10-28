package fr.nathanaudvard.emp.config

import io.ktor.server.application.Application
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.hooks.ResponseSent
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.request.httpMethod
import io.ktor.server.request.uri
import io.ktor.util.AttributeKey
import kotlin.time.TimeSource

private val accessToken = Regex("""^/access/[0-9a-f]{16,}""")

private val CallStart =AttributeKey<TimeSource.Monotonic.ValueTimeMark>("CallStart")

// CallLogging is JVM-only
private val RequestLogging = createApplicationPlugin("RequestLogging") {
    onCall { call ->
        call.attributes.put(CallStart, TimeSource.Monotonic.markNow())
    }
    on(ResponseSent) { call ->
        // StatusPages can respond a second time for the same call
        val elapsed = call.attributes.takeOrNull(CallStart)?.elapsedNow() ?: return@on
        // An access link's token opens a session: it is never written to the logs
        val uri = call.request.uri.replace(accessToken, "/access/…")
        application.log.info("${call.response.status()?.value} ${call.request.httpMethod.value} $uri in $elapsed")
    }
}

fun Application.configureMonitoring() {
    install(RequestLogging)
}
