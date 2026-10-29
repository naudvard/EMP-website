package fr.nathanaudvard.emp.database

import fr.nathanaudvard.emp.db.EmpDatabase
import fr.nathanaudvard.emp.domain.model.ExportedAlliance
import fr.nathanaudvard.emp.domain.model.ExportedPact
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

    suspend fun latest(tag: String): ExportedAlliance? = withContext(Dispatchers.IO) {
        queries.transactionWithResult {
            val row = queries.latest(tag).executeAsOneOrNull() ?: return@transactionWithResult null
            ExportedAlliance(
                tag = row.tag,
                name = row.name,
                playersCount = row.players_count,
                totalField = row.total_field,
                totalBuildingScore = row.total_building_score,
                totalTechnologyScore = row.total_technology_score,
                totalTrophyScore = row.total_trophy_score,
                wars = row.wars,
                pacts = queries.pacts(row.id, ::ExportedPact).executeAsList(),
            )
        }
    }
}
