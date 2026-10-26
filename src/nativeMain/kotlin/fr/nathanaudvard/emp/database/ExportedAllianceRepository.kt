package fr.nathanaudvard.emp.database

import fr.nathanaudvard.emp.db.EmpDatabase
import fr.nathanaudvard.emp.domain.model.ExportedAlliance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

class ExportedAllianceRepository(database: EmpDatabase) {
    private val queries = database.exportedAllianceQueries

    // Replaces these alliances only: the version keeps the other alliances synced before
    suspend fun replaceVersion(version: String, tags: Set<String>, alliances: List<ExportedAlliance>) =
        withContext(Dispatchers.IO) {
            queries.transaction {
                // Pacts are deleted by cascade
                queries.deleteVersion(version, tags)
                alliances.forEach { alliance ->
                    val id = with(alliance) {
                        queries.insert(
                            version, tag, name, playersCount, totalField,
                            totalBuildingScore, totalTechnologyScore, totalTrophyScore, wars,
                        ).executeAsOne()
                    }
                    alliance.pacts.forEach {
                        queries.insertPact(
                            id,
                            it.tag,
                            it.name,
                            it.description
                        )
                    }
                }
            }
        }
}
