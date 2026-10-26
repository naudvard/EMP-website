package fr.nathanaudvard.emp

import fr.nathanaudvard.emp.api.controllers.allianceController
import fr.nathanaudvard.emp.api.controllers.exportController
import fr.nathanaudvard.emp.api.controllers.healthController
import fr.nathanaudvard.emp.config.AllianceConfig
import fr.nathanaudvard.emp.domain.usecase.ComputeWagesUseCase
import fr.nathanaudvard.emp.domain.usecase.GetAllianceMembersUseCase
import fr.nathanaudvard.emp.domain.usecase.GetWageHistoryUseCase
import fr.nathanaudvard.emp.domain.usecase.SavePlayerLevelUseCase
import fr.nathanaudvard.emp.domain.usecase.SyncExportedAlliancesUseCase
import fr.nathanaudvard.emp.domain.usecase.SyncExportedPlayersUseCase
import fr.nathanaudvard.emp.web.controllers.homeController
import fr.nathanaudvard.emp.web.controllers.levelsController
import fr.nathanaudvard.emp.web.controllers.wagesController
import io.ktor.server.application.Application
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.routing.routing

suspend fun Application.configureRouting() {
    val syncAlliances: SyncExportedAlliancesUseCase = dependencies.resolve()
    val syncPlayers: SyncExportedPlayersUseCase = dependencies.resolve()
    val computeWagesUseCase: ComputeWagesUseCase = dependencies.resolve()
    val getWageHistory: GetWageHistoryUseCase = dependencies.resolve()
    val getMembers: GetAllianceMembersUseCase = dependencies.resolve()
    val saveLevel: SavePlayerLevelUseCase = dependencies.resolve()
    val alliance: AllianceConfig = dependencies.resolve()

    routing {
        healthController()
        exportController(syncAlliances, syncPlayers)
        allianceController(computeWagesUseCase)
        homeController(getWageHistory, getMembers, alliance.tag)
        levelsController(getMembers, saveLevel, alliance.tag)
        wagesController(getWageHistory, alliance.tag)
    }
}
