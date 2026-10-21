package fr.nathanaudvard.emp.output.database

import fr.nathanaudvard.emp.db.EmpDatabase
import fr.nathanaudvard.emp.domain.model.Alliance
import fr.nathanaudvard.emp.domain.model.NewAlliance
import fr.nathanaudvard.emp.domain.model.Pact
import fr.nathanaudvard.emp.domain.port.AllianceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

class SqlAllianceRepository(database: EmpDatabase) : AllianceRepository {
    private val queries = database.allianceQueries

    override suspend fun saveVersion(version: String, alliances: List<NewAlliance>): List<Alliance> = withContext(Dispatchers.IO) {
        queries.transactionWithResult {
            alliances.map { alliance ->
                val id = with(alliance) {
                    queries.upsert(
                        version, tag, name, playersCount, totalField,
                        totalBuildingScore, totalTechnologyScore, totalTrophyScore, wars,
                    ).executeAsOne()
                }

                queries.deletePacts(id)
                val pacts = alliance.pacts.map { pact ->
                    val pactId = queries.insertPact(id, pact.tag, pact.name, pact.description).executeAsOne()
                    Pact(id = pactId, tag = pact.tag, name = pact.name, description = pact.description)
                }

                alliance.toAlliance(id, version, pacts)
            }
        }
    }
}

private fun NewAlliance.toAlliance(id: Long, version: String, pacts: List<Pact>) = Alliance(
    id = id,
    version = version,
    tag = tag,
    name = name,
    playersCount = playersCount,
    totalField = totalField,
    totalBuildingScore = totalBuildingScore,
    totalTechnologyScore = totalTechnologyScore,
    totalTrophyScore = totalTrophyScore,
    wars = wars,
    pacts = pacts,
)
