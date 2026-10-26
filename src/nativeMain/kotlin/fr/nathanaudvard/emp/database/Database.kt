package fr.nathanaudvard.emp.database

import app.cash.sqldelight.ColumnAdapter
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import co.touchlab.sqliter.JournalMode
import co.touchlab.sqliter.SynchronousFlag
import fr.nathanaudvard.emp.db.EmpDatabase
import fr.nathanaudvard.emp.db.Exported_alliances
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.serialization.json.Json

fun createSqlDriver(databasePath: String): SqlDriver {
    val file = Path(databasePath)
    val directory = file.parent ?: Path(".")
    SystemFileSystem.createDirectories(directory)

    return NativeSqliteDriver(
        schema = EmpDatabase.Schema,
        name = file.name,
        onConfiguration = { config ->
            config.copy(
                journalMode = JournalMode.WAL,
                extendedConfig = config.extendedConfig.copy(
                    foreignKeyConstraints = true,
                    busyTimeout = 5_000,
                    synchronousFlag = SynchronousFlag.NORMAL,
                    // Without it, sqliter stores the database in the user home directory
                    basePath = SystemFileSystem.resolve(directory).toString(),
                ),
            )
        },
    )
}

private val stringListAdapter = object : ColumnAdapter<List<String>, String> {
    override fun decode(databaseValue: String): List<String> = Json.decodeFromString(databaseValue)
    override fun encode(value: List<String>): String = Json.encodeToString(value)
}

fun createDatabase(driver: SqlDriver): EmpDatabase =
    EmpDatabase(
        driver,
        exported_alliancesAdapter = Exported_alliances.Adapter(warsAdapter = stringListAdapter)
    )
