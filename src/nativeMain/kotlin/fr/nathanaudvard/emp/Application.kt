package fr.nathanaudvard.emp

import fr.nathanaudvard.emp.config.configureMonitoring
import fr.nathanaudvard.emp.config.configureSerialization
import fr.nathanaudvard.emp.config.configureStatusPages
import fr.nathanaudvard.emp.input.http.routes.configureRouting
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.CommandLineConfig
import io.ktor.server.engine.embeddedServer
import io.ktor.server.resources.Resources

fun main(args: Array<String>) {
    // Kotlin/Native cannot load modules by name from application.yaml: they are registered here
    val commandLine = CommandLineConfig(args)
    embeddedServer(CIO, commandLine.environment, { takeFrom(commandLine.engineConfig) }, Application::module)
        .start(wait = true)
}

suspend fun Application.module() {
    install(Resources)

    configureSerialization()
    configureMonitoring()
    configureStatusPages()
    configureDependencies()
    configureRouting()
}
