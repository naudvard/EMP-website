package fr.nathanaudvard.emp

import fr.nathanaudvard.emp.config.AppConfig
import fr.nathanaudvard.emp.database.AccessRepository
import fr.nathanaudvard.emp.database.ExportedPlayerRepository
import fr.nathanaudvard.emp.database.PlayerRepository
import fr.nathanaudvard.emp.database.createDatabase
import fr.nathanaudvard.emp.database.createSqlDriver
import fr.nathanaudvard.emp.domain.helper.AccessHelper
import fr.nathanaudvard.emp.domain.service.AccessService
import fr.nathanaudvard.emp.web.pages.href
import fr.nathanaudvard.emp.web.resources.Access
import io.ktor.server.config.getAs
import io.ktor.server.engine.CommandLineConfig
import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess
import kotlin.time.Clock

const val CHIEF_LINK_COMMAND = "chief-link"

// `emp chief-link <pseudo> [-config=…]`: makes the member a chief and prints their link, for the first chief, whom no
// one can send one to. The players export must have been synced
fun printChiefLink(args: List<String>) {
    val pseudo = args.firstOrNull() ?: exit("Usage: emp $CHIEF_LINK_COMMAND <pseudo>")
    val config = CommandLineConfig(args.drop(1).toTypedArray()).environment.config.property("app").getAs<AppConfig>()
    val driver = createSqlDriver(config.database.path)
    try {
        val database = createDatabase(driver)
        val accessRepository = AccessRepository(database)
        val access = AccessService(
            AccessHelper(accessRepository),
            accessRepository,
            PlayerRepository(database),
            ExportedPlayerRepository(database),
            Clock.System,
        )
        val token = runBlocking { access.createChiefLink(config.alliance.tag, pseudo) }
            ?: exit("No member of ${config.alliance.tag} is called $pseudo in the latest players export")
        println("${config.publicUrl.trimEnd('/')}${href(Access.Link(token = token))}")
    } finally {
        driver.close()
    }
}

private fun exit(message: String): Nothing {
    println(message)
    exitProcess(1)
}
