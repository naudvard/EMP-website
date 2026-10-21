package fr.nathanaudvard.emp

import fr.nathanaudvard.emp.api.controllers.exportController
import fr.nathanaudvard.emp.api.controllers.healthController
import fr.nathanaudvard.emp.config.AllianceConfig
import fr.nathanaudvard.emp.database.ExportedPlayerRepository
import fr.nathanaudvard.emp.database.PlayerRepository
import fr.nathanaudvard.emp.domain.usecase.SyncExportedAlliances
import fr.nathanaudvard.emp.domain.usecase.SyncExportedPlayers
import fr.nathanaudvard.emp.web.controllers.layingTimeController
import io.ktor.server.application.Application
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.plugins.di.resolve
import io.ktor.server.routing.routing

suspend fun Application.configureRouting() {
    val syncAlliances: SyncExportedAlliances = dependencies.resolve()
    val syncPlayers: SyncExportedPlayers = dependencies.resolve()
    val exportedPlayers: ExportedPlayerRepository = dependencies.resolve()
    val players: PlayerRepository = dependencies.resolve()
    val alliance: AllianceConfig = dependencies.resolve()

    routing {
        healthController()
        exportController(syncAlliances, syncPlayers)
        layingTimeController(exportedPlayers, players, alliance.tag)
    }
}
