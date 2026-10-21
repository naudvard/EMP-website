package fr.nathanaudvard.emp.domain.usecase

import fr.nathanaudvard.emp.domain.model.Alliance
import fr.nathanaudvard.emp.domain.model.ExportType
import fr.nathanaudvard.emp.domain.model.ExportVersion
import fr.nathanaudvard.emp.domain.model.NewAlliance
import fr.nathanaudvard.emp.domain.model.Player
import fr.nathanaudvard.emp.domain.port.AllianceRepository
import fr.nathanaudvard.emp.domain.port.ExportSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking

class SyncAlliancesTest {
    private val empire = NewAlliance(
        tag = "EMP",
        name = "Empire",
        playersCount = 12,
        totalField = 1_000,
        totalBuildingScore = 100,
        totalTechnologyScore = 50,
        totalTrophyScore = 0,
        wars = emptyList(),
        pacts = emptyList(),
    )

    private val exports = object : ExportSource {
        override suspend fun latestVersion(type: ExportType) = "202610201200"
        override suspend fun alliances(version: String) = listOf(empire)
        override suspend fun players(version: String) = emptyList<Player>()
    }

    private val savedVersions = mutableListOf<String>()

    private val repository = object : AllianceRepository {
        override suspend fun saveVersion(version: String, alliances: List<NewAlliance>): List<Alliance> {
            savedVersions += version
            return emptyList()
        }
    }

    private val syncAlliances = SyncAlliances(exports, repository)

    @Test
    fun `saves latest under the resolved version`() = runBlocking {
        syncAlliances(ExportVersion.Latest)

        assertEquals(listOf("202610201200"), savedVersions)
    }

    @Test
    fun `saves an exact version as is`() = runBlocking {
        syncAlliances(ExportVersion.parse("202601010000"))

        assertEquals(listOf("202601010000"), savedVersions)
    }
}
