package fr.nathanaudvard.emp

import app.cash.sqldelight.db.SqlDriver
import fr.nathanaudvard.emp.config.AllianceConfig
import fr.nathanaudvard.emp.config.AppConfig
import fr.nathanaudvard.emp.database.AccessRepository
import fr.nathanaudvard.emp.database.ExportedAllianceRepository
import fr.nathanaudvard.emp.database.ExportedPlayerRepository
import fr.nathanaudvard.emp.database.PlayerRepository
import fr.nathanaudvard.emp.database.WageRepository
import fr.nathanaudvard.emp.database.createDatabase
import fr.nathanaudvard.emp.database.createSqlDriver
import fr.nathanaudvard.emp.domain.usecase.ComputeWagesUseCase
import fr.nathanaudvard.emp.domain.usecase.CreateChiefLinkUseCase
import fr.nathanaudvard.emp.domain.usecase.CreateDeviceCodeUseCase
import fr.nathanaudvard.emp.domain.usecase.DeleteConvoyUseCase
import fr.nathanaudvard.emp.domain.usecase.GetAllianceMembersUseCase
import fr.nathanaudvard.emp.domain.usecase.GetChiefsUseCase
import fr.nathanaudvard.emp.domain.usecase.GetDebtsUseCase
import fr.nathanaudvard.emp.domain.usecase.GetMemberAccessesUseCase
import fr.nathanaudvard.emp.domain.usecase.GetWageHistoryUseCase
import fr.nathanaudvard.emp.domain.usecase.GetWageSettlementsUseCase
import fr.nathanaudvard.emp.domain.usecase.IdentifyViewerUseCase
import fr.nathanaudvard.emp.domain.usecase.OpenAccessLinkUseCase
import fr.nathanaudvard.emp.domain.usecase.RecordConvoysUseCase
import fr.nathanaudvard.emp.domain.usecase.ReplaceAccessLinkUseCase
import fr.nathanaudvard.emp.domain.usecase.SavePlayerLevelUseCase
import fr.nathanaudvard.emp.domain.usecase.SaveWageSettlementsUseCase
import fr.nathanaudvard.emp.domain.usecase.SendAccessLinkUseCase
import fr.nathanaudvard.emp.domain.usecase.SignInWithCodeUseCase
import fr.nathanaudvard.emp.domain.usecase.SyncExportedAlliancesUseCase
import fr.nathanaudvard.emp.domain.usecase.SyncExportedPlayersUseCase
import fr.nathanaudvard.emp.fourmizzz.FourmizzzClient
import fr.nathanaudvard.emp.service.AccessService
import fr.nathanaudvard.emp.service.AttemptLimiter
import fr.nathanaudvard.emp.service.ExportService
import fr.nathanaudvard.emp.service.PlayerService
import fr.nathanaudvard.emp.service.WageService
import io.ktor.client.HttpClient
import io.ktor.server.application.Application
import io.ktor.server.config.getAs
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.plugins.di.provide
import io.ktor.server.plugins.di.resolve
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

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

        provide<HttpClient> { HttpClient() }
        provide<FourmizzzClient> { FourmizzzClient(resolve(), config.fourmizzz) }

        provide(::ExportService)
        provide(::PlayerService)
        provide(::WageService)
        provide(::AccessService)
        // Device codes typed on the access screen
        provide { AttemptLimiter(perClient = 10, total = 50, window = 10.minutes) }

        provide(::SyncExportedAlliancesUseCase)
        provide(::SyncExportedPlayersUseCase)
        provide(::ComputeWagesUseCase)
        provide(::GetWageHistoryUseCase)
        provide(::GetAllianceMembersUseCase)
        provide(::SavePlayerLevelUseCase)
        provide(::GetWageSettlementsUseCase)
        provide(::GetDebtsUseCase)
        provide(::SaveWageSettlementsUseCase)
        provide(::RecordConvoysUseCase)
        provide(::DeleteConvoyUseCase)
        provide(::IdentifyViewerUseCase)
        provide(::OpenAccessLinkUseCase)
        provide(::SignInWithCodeUseCase)
        provide(::CreateDeviceCodeUseCase)
        provide(::GetChiefsUseCase)
        provide(::GetMemberAccessesUseCase)
        provide(::SendAccessLinkUseCase)
        provide(::ReplaceAccessLinkUseCase)
        provide(::CreateChiefLinkUseCase)
    }
}
