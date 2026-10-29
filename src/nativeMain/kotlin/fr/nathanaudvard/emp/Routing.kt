package fr.nathanaudvard.emp

import fr.nathanaudvard.emp.api.controllers.allianceController
import fr.nathanaudvard.emp.api.controllers.exportController
import fr.nathanaudvard.emp.api.controllers.healthController
import fr.nathanaudvard.emp.config.AppConfig
import fr.nathanaudvard.emp.config.apiAccess
import fr.nathanaudvard.emp.config.memberAccess
import fr.nathanaudvard.emp.domain.usecase.ComputeWagesNowUseCase
import fr.nathanaudvard.emp.domain.usecase.ComputeWagesUseCase
import fr.nathanaudvard.emp.domain.usecase.ConfirmPlayerLevelsUseCase
import fr.nathanaudvard.emp.domain.usecase.CreateDeviceCodeUseCase
import fr.nathanaudvard.emp.domain.usecase.DeclareConvoyUseCase
import fr.nathanaudvard.emp.domain.usecase.DeleteConvoyUseCase
import fr.nathanaudvard.emp.domain.usecase.GetAllianceSettingsUseCase
import fr.nathanaudvard.emp.domain.usecase.GetAllianceUseCase
import fr.nathanaudvard.emp.domain.usecase.GetChiefsUseCase
import fr.nathanaudvard.emp.domain.usecase.GetMemberUseCase
import fr.nathanaudvard.emp.domain.usecase.GetMyOverviewUseCase
import fr.nathanaudvard.emp.domain.usecase.GetWageHistoryUseCase
import fr.nathanaudvard.emp.domain.usecase.GetWagesPanelUseCase
import fr.nathanaudvard.emp.domain.usecase.IdentifyViewerUseCase
import fr.nathanaudvard.emp.domain.usecase.OpenAccessLinkUseCase
import fr.nathanaudvard.emp.domain.usecase.PasteConvoysUseCase
import fr.nathanaudvard.emp.domain.usecase.ReplaceAccessLinkUseCase
import fr.nathanaudvard.emp.domain.usecase.SaveAllianceSettingsUseCase
import fr.nathanaudvard.emp.domain.usecase.SavePlayerLevelUseCase
import fr.nathanaudvard.emp.domain.usecase.SaveWageSettlementUseCase
import fr.nathanaudvard.emp.domain.usecase.SendAccessLinkUseCase
import fr.nathanaudvard.emp.domain.usecase.SetChiefUseCase
import fr.nathanaudvard.emp.domain.usecase.SignInWithCodeUseCase
import fr.nathanaudvard.emp.domain.usecase.SyncExportedAlliancesUseCase
import fr.nathanaudvard.emp.domain.usecase.SyncExportedPlayersUseCase
import fr.nathanaudvard.emp.domain.usecase.SyncFourmizzzUseCase
import fr.nathanaudvard.emp.domain.usecase.UndoLevelChangeUseCase
import fr.nathanaudvard.emp.web.controllers.accessController
import fr.nathanaudvard.emp.web.controllers.alliancePageController
import fr.nathanaudvard.emp.web.controllers.assetsController
import fr.nathanaudvard.emp.web.controllers.deviceController
import fr.nathanaudvard.emp.web.controllers.formerPagesController
import fr.nathanaudvard.emp.web.controllers.homeController
import fr.nathanaudvard.emp.web.controllers.meController
import fr.nathanaudvard.emp.web.controllers.memberController
import fr.nathanaudvard.emp.web.controllers.wagesController
import io.ktor.server.application.Application
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.routing.routing
import kotlin.time.Clock

suspend fun Application.configureRouting() {
    val syncAlliances: SyncExportedAlliancesUseCase = dependencies.resolve()
    val syncPlayers: SyncExportedPlayersUseCase = dependencies.resolve()
    val syncFourmizzz: SyncFourmizzzUseCase = dependencies.resolve()
    val getWagesPanel: GetWagesPanelUseCase = dependencies.resolve()
    val getSettings: GetAllianceSettingsUseCase = dependencies.resolve()
    val saveSettings: SaveAllianceSettingsUseCase = dependencies.resolve()
    val computeNow: ComputeWagesNowUseCase = dependencies.resolve()
    val computeWagesUseCase: ComputeWagesUseCase = dependencies.resolve()
    val getWageHistory: GetWageHistoryUseCase = dependencies.resolve()
    val getAlliance: GetAllianceUseCase = dependencies.resolve()
    val getMember: GetMemberUseCase = dependencies.resolve()
    val saveLevel: SavePlayerLevelUseCase = dependencies.resolve()
    val saveSettlement: SaveWageSettlementUseCase = dependencies.resolve()
    val setChief: SetChiefUseCase = dependencies.resolve()
    val deleteConvoy: DeleteConvoyUseCase = dependencies.resolve()
    val identify: IdentifyViewerUseCase = dependencies.resolve()
    val openLink: OpenAccessLinkUseCase = dependencies.resolve()
    val signInWithCode: SignInWithCodeUseCase = dependencies.resolve()
    val createDeviceCode: CreateDeviceCodeUseCase = dependencies.resolve()
    val getChiefs: GetChiefsUseCase = dependencies.resolve()
    val sendLink: SendAccessLinkUseCase = dependencies.resolve()
    val replaceLink: ReplaceAccessLinkUseCase = dependencies.resolve()
    val getOverview: GetMyOverviewUseCase = dependencies.resolve()
    val confirmLevels: ConfirmPlayerLevelsUseCase = dependencies.resolve()
    val undoLevelChange: UndoLevelChangeUseCase = dependencies.resolve()
    val declareConvoy: DeclareConvoyUseCase = dependencies.resolve()
    val pasteConvoys: PasteConvoysUseCase = dependencies.resolve()
    val clock: Clock = dependencies.resolve()
    val config: AppConfig = dependencies.resolve()
    val tag = config.alliance.tag

    routing {
        healthController()
        assetsController()
        accessController(identify, openLink, signInWithCode, getChiefs, tag)
        formerPagesController()
        apiAccess(identify, tag, config.api.token) {
            exportController(syncAlliances, syncPlayers)
            allianceController(computeWagesUseCase)
        }
        memberAccess(identify, tag) {
            homeController()
            meController(getOverview, saveLevel, confirmLevels, undoLevelChange, declareConvoy, pasteConvoys, deleteConvoy, clock, tag)
            wagesController(getWageHistory, getWagesPanel, getSettings, saveSettings, computeNow, clock, config.publicUrl, tag)
            alliancePageController(getAlliance, syncFourmizzz, clock, tag)
            memberController(
                getMember, saveLevel, confirmLevels, undoLevelChange, declareConvoy, pasteConvoys, deleteConvoy,
                saveSettlement, setChief, sendLink, replaceLink, clock, config.publicUrl, tag,
            )
            deviceController(createDeviceCode, getOverview, clock, tag)
        }
    }
}
