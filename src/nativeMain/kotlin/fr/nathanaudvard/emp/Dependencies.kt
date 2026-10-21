package fr.nathanaudvard.emp

import app.cash.sqldelight.db.SqlDriver
import fr.nathanaudvard.emp.config.AllianceConfig
import fr.nathanaudvard.emp.config.AppConfig
import fr.nathanaudvard.emp.database.ExportedAllianceRepository
import fr.nathanaudvard.emp.database.ExportedPlayerRepository
import fr.nathanaudvard.emp.database.PlayerRepository
import fr.nathanaudvard.emp.database.createDatabase
import fr.nathanaudvard.emp.database.createSqlDriver
import fr.nathanaudvard.emp.domain.usecase.SyncExportedAlliances
import fr.nathanaudvard.emp.domain.usecase.SyncExportedPlayers
import fr.nathanaudvard.emp.fourmizzz.FourmizzzClient
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
        provide<AllianceConfig> { config.alliance }

        // SQLDelight's Closeable is not AutoCloseable: DI does not close it by itself
        provide<SqlDriver> { createSqlDriver(config.database.path) } cleanup { it.close() }
        provide(::createDatabase)
        provide(::ExportedAllianceRepository)
        provide(::ExportedPlayerRepository)
        provide(::PlayerRepository)

        provide<HttpClient> { HttpClient() }
        provide<FourmizzzClient> { FourmizzzClient(resolve(), config.fourmizzz) }

        provide(::SyncExportedAlliances)
        provide(::SyncExportedPlayers)
    }
}
