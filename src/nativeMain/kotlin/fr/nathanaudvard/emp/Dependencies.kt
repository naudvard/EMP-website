package fr.nathanaudvard.emp

import app.cash.sqldelight.db.SqlDriver
import fr.nathanaudvard.emp.config.AppConfig
import fr.nathanaudvard.emp.domain.port.AllianceRepository
import fr.nathanaudvard.emp.domain.port.ExportSource
import fr.nathanaudvard.emp.domain.port.PlayerRepository
import fr.nathanaudvard.emp.domain.usecase.SyncAlliances
import fr.nathanaudvard.emp.domain.usecase.SyncPlayers
import fr.nathanaudvard.emp.output.database.SqlAllianceRepository
import fr.nathanaudvard.emp.output.database.SqlPlayerRepository
import fr.nathanaudvard.emp.output.database.createDatabase
import fr.nathanaudvard.emp.output.database.createSqlDriver
import fr.nathanaudvard.emp.output.http.FourmizzzClient
import io.ktor.client.HttpClient
import io.ktor.server.application.Application
import io.ktor.server.config.getAs
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.plugins.di.provide
import io.ktor.server.plugins.di.resolve

// Native has no reflection: every dependency is built explicitly. AutoCloseable ones are closed on shutdown.
fun Application.configureDependencies() {
    val config = environment.config.property("app").getAs<AppConfig>()

    dependencies {
        // SQLDelight's Closeable is not AutoCloseable: DI does not close it by itself
        provide<SqlDriver> { createSqlDriver(config.database.path) } cleanup { it.close() }
        provide(::createDatabase)
        provide<AllianceRepository> { SqlAllianceRepository(resolve()) }
        provide<PlayerRepository> { SqlPlayerRepository(resolve()) }

        provide<HttpClient> { HttpClient() }
        provide<ExportSource> { FourmizzzClient(resolve(), config.fourmizzz) }

        provide(::SyncAlliances)
        provide(::SyncPlayers)
    }
}
