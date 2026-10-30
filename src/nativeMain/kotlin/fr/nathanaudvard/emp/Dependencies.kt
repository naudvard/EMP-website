package fr.nathanaudvard.emp

import app.cash.sqldelight.db.SqlDriver
import fr.nathanaudvard.emp.config.AllianceConfig
import fr.nathanaudvard.emp.config.AppConfig
import fr.nathanaudvard.emp.database.AccessRepository
import fr.nathanaudvard.emp.database.AllianceSettingsRepository
import fr.nathanaudvard.emp.database.ExportedAllianceRepository
import fr.nathanaudvard.emp.database.ExportedPlayerRepository
import fr.nathanaudvard.emp.database.PlayerRepository
import fr.nathanaudvard.emp.database.WageRepository
import fr.nathanaudvard.emp.database.createDatabase
import fr.nathanaudvard.emp.database.createSqlDriver
import fr.nathanaudvard.emp.domain.helper.AccessHelper
import fr.nathanaudvard.emp.domain.helper.ExportHelper
import fr.nathanaudvard.emp.domain.helper.PlayerHelper
import fr.nathanaudvard.emp.domain.helper.WageHelper
import fr.nathanaudvard.emp.domain.service.AccessService
import fr.nathanaudvard.emp.domain.service.AllianceService
import fr.nathanaudvard.emp.domain.service.ConvoyService
import fr.nathanaudvard.emp.domain.service.ExportService
import fr.nathanaudvard.emp.domain.service.PlayerService
import fr.nathanaudvard.emp.domain.service.WageService
import fr.nathanaudvard.emp.fourmizzz.FourmizzzClient
import io.ktor.client.HttpClient
import io.ktor.server.application.Application
import io.ktor.server.config.getAs
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.plugins.di.provide
import io.ktor.server.plugins.di.resolve
import kotlin.time.Clock

// Native has no reflection: every dependency is built explicitly. AutoCloseable ones are closed on shutdown.
fun Application.configureDependencies() {
    val config = environment.config.property("app").getAs<AppConfig>()

    dependencies {
        provide<AppConfig> { config }
        provide<AllianceConfig> { config.alliance }
        provide<Clock> { Clock.System }

        // SQLDelight's Closeable is not AutoCloseable: DI does not close it by itself
        provide<SqlDriver> { createSqlDriver(config.database.path) } cleanup { it.close() }
        provide(::createDatabase)
        provide(::ExportedAllianceRepository)
        provide(::ExportedPlayerRepository)
        provide(::PlayerRepository)
        provide(::WageRepository)
        provide(::AccessRepository)
        provide(::AllianceSettingsRepository)

        provide<HttpClient> { HttpClient() }
        provide<FourmizzzClient> { FourmizzzClient(resolve(), config.fourmizzz) }

        provide(::ExportHelper)
        provide(::PlayerHelper)
        provide(::WageHelper)
        provide(::AccessHelper)

        provide(::ExportService)
        provide(::AccessService)
        // A constructor reference takes at most 6 parameters
        provide<AllianceService> {
            AllianceService(resolve(), resolve(), resolve(), resolve(), resolve(), resolve(), resolve(), resolve())
        }
        provide(::PlayerService)
        provide(::ConvoyService)
        provide<WageService> {
            WageService(resolve(), resolve(), resolve(), resolve(), resolve(), resolve(), resolve(), resolve())
        }
    }
}
