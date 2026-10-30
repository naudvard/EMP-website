package fr.nathanaudvard.emp

import fr.nathanaudvard.emp.api.controllers.allianceController
import fr.nathanaudvard.emp.api.controllers.exportController
import fr.nathanaudvard.emp.api.controllers.healthController
import fr.nathanaudvard.emp.config.AppConfig
import fr.nathanaudvard.emp.config.apiAccess
import fr.nathanaudvard.emp.config.memberAccess
import fr.nathanaudvard.emp.domain.service.AccessService
import fr.nathanaudvard.emp.domain.service.AllianceService
import fr.nathanaudvard.emp.domain.service.ConvoyService
import fr.nathanaudvard.emp.domain.service.ExportService
import fr.nathanaudvard.emp.domain.service.PlayerService
import fr.nathanaudvard.emp.domain.service.WageService
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
    val exports: ExportService = dependencies.resolve()
    val access: AccessService = dependencies.resolve()
    val alliance: AllianceService = dependencies.resolve()
    val players: PlayerService = dependencies.resolve()
    val convoys: ConvoyService = dependencies.resolve()
    val wages: WageService = dependencies.resolve()
    val clock: Clock = dependencies.resolve()
    val config: AppConfig = dependencies.resolve()
    val tag = config.alliance.tag

    routing {
        healthController()
        assetsController()
        accessController(access, tag)
        formerPagesController()
        apiAccess {
            exportController(exports)
            allianceController(wages)
        }
        memberAccess {
            homeController()
            meController(players, convoys, clock, tag)
            wagesController(wages, clock, tag)
            alliancePageController(alliance, clock, tag)
            memberController(alliance, players, convoys, access, clock, config.publicUrl, tag)
            deviceController(access)
        }
    }
}
