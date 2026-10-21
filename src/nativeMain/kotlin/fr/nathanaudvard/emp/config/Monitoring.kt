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

private val CallStart = AttributeKey<TimeSource.Monotonic.ValueTimeMark>("CallStart")

// CallLogging is JVM-only
private val RequestLogging = createApplicationPlugin("RequestLogging") {
    onCall { call ->
        call.attributes.put(CallStart, TimeSource.Monotonic.markNow())
    }
    on(ResponseSent) { call ->
        // StatusPages can respond a second time for the same call
        val elapsed = call.attributes.takeOrNull(CallStart)?.elapsedNow() ?: return@on
        application.log.info(
            "${call.response.status()?.value} ${call.request.httpMethod.value} ${call.request.uri} in $elapsed"
        )
    }
}

fun Application.configureMonitoring() {
    install(RequestLogging)
}
