package fr.nathanaudvard.emp

import fr.nathanaudvard.emp.config.configureHttp
import fr.nathanaudvard.emp.config.configureIdentity
import fr.nathanaudvard.emp.config.configureMonitoring
import fr.nathanaudvard.emp.config.configureSerialization
import fr.nathanaudvard.emp.config.configureStatusPages
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.CommandLineConfig
import io.ktor.server.engine.embeddedServer
import io.ktor.server.resources.Resources

fun main(args: Array<String>) {
    if (args.firstOrNull() == CHIEF_LINK_COMMAND) return printChiefLink(args.drop(1))
    // Kotlin/Native cannot load modules by name from application.yaml: they are registered here
    val commandLine = CommandLineConfig(args)
    embeddedServer(
        CIO,
        commandLine.environment,
        { takeFrom(commandLine.engineConfig) },
        Application::module
    )
        .start(wait = true)
}

suspend fun Application.module() {
    install(Resources)

    configureHttp()
    configureSerialization()
    configureMonitoring()
    configureStatusPages()
    configureDependencies()
    configureIdentity()
    configureRouting()
}
