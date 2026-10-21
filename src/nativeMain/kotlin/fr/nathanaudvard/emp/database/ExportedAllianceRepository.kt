package fr.nathanaudvard.emp.database

import fr.nathanaudvard.emp.db.EmpDatabase
import fr.nathanaudvard.emp.domain.model.ExportedAlliance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

class ExportedAllianceRepository(database: EmpDatabase) {
    private val queries = database.exportedAllianceQueries

    suspend fun replaceVersion(version: String, alliances: List<ExportedAlliance>) = withContext(Dispatchers.IO) {
        queries.transaction {
            // Pacts are deleted by cascade
            queries.deleteVersion(version)
            alliances.forEach { alliance ->
                val id = with(alliance) {
                    queries.insert(
                        version, tag, name, playersCount, totalField,
                        totalBuildingScore, totalTechnologyScore, totalTrophyScore, wars,
                    ).executeAsOne()
                }
                alliance.pacts.forEach { queries.insertPact(id, it.tag, it.name, it.description) }
            }
        }
    }
}
