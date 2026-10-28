package fr.nathanaudvard.emp

import fr.nathanaudvard.emp.api.controllers.allianceController
import fr.nathanaudvard.emp.api.controllers.exportController
import fr.nathanaudvard.emp.api.controllers.healthController
import fr.nathanaudvard.emp.config.AppConfig
import fr.nathanaudvard.emp.config.apiAccess
import fr.nathanaudvard.emp.config.memberAccess
import fr.nathanaudvard.emp.domain.usecase.ComputeWagesUseCase
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
import fr.nathanaudvard.emp.web.controllers.accessController
import fr.nathanaudvard.emp.web.controllers.accessLinksController
import fr.nathanaudvard.emp.web.controllers.assetsController
import fr.nathanaudvard.emp.web.controllers.debtsController
import fr.nathanaudvard.emp.web.controllers.deviceController
import fr.nathanaudvard.emp.web.controllers.homeController
import fr.nathanaudvard.emp.web.controllers.levelsController
import fr.nathanaudvard.emp.web.controllers.myDeliveriesController
import fr.nathanaudvard.emp.web.controllers.wagesController
import io.ktor.server.application.Application
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.routing.routing
import kotlin.time.Clock

suspend fun Application.configureRouting() {
    val syncAlliances: SyncExportedAlliancesUseCase = dependencies.resolve()
    val syncPlayers: SyncExportedPlayersUseCase = dependencies.resolve()
    val computeWagesUseCase: ComputeWagesUseCase = dependencies.resolve()
    val getWageHistory: GetWageHistoryUseCase = dependencies.resolve()
    val getMembers: GetAllianceMembersUseCase = dependencies.resolve()
    val saveLevel: SavePlayerLevelUseCase = dependencies.resolve()
    val getSettlements: GetWageSettlementsUseCase = dependencies.resolve()
    val getDebts: GetDebtsUseCase = dependencies.resolve()
    val saveSettlements: SaveWageSettlementsUseCase = dependencies.resolve()
    val recordConvoys: RecordConvoysUseCase = dependencies.resolve()
    val deleteConvoy: DeleteConvoyUseCase = dependencies.resolve()
    val identify: IdentifyViewerUseCase = dependencies.resolve()
    val openLink: OpenAccessLinkUseCase = dependencies.resolve()
    val signInWithCode: SignInWithCodeUseCase = dependencies.resolve()
    val createDeviceCode: CreateDeviceCodeUseCase = dependencies.resolve()
    val getChiefs: GetChiefsUseCase = dependencies.resolve()
    val getAccesses: GetMemberAccessesUseCase = dependencies.resolve()
    val sendLink: SendAccessLinkUseCase = dependencies.resolve()
    val replaceLink: ReplaceAccessLinkUseCase = dependencies.resolve()
    val clock: Clock = dependencies.resolve()
    val config: AppConfig = dependencies.resolve()
    val tag = config.alliance.tag

    routing {
        healthController()
        assetsController()
        accessController(identify, openLink, signInWithCode, getChiefs, tag)
        apiAccess(identify, tag, config.api.token) {
            exportController(syncAlliances, syncPlayers)
            allianceController(computeWagesUseCase)
        }
        memberAccess(identify, tag) {
            homeController(getWageHistory, getMembers, tag)
            levelsController(getMembers, saveLevel, tag)
            wagesController(getWageHistory, tag)
            myDeliveriesController(getWageHistory, recordConvoys, deleteConvoy, tag)
            debtsController(getMembers, getSettlements, getDebts, saveSettlements, tag)
            deviceController(createDeviceCode)
            accessLinksController(getAccesses, sendLink, replaceLink, clock, config.publicUrl, tag)
        }
    }
}
