package fr.nathanaudvard.emp.domain.port

import fr.nathanaudvard.emp.domain.model.ExportType
import fr.nathanaudvard.emp.domain.model.NewAlliance
import fr.nathanaudvard.emp.domain.model.Player

interface ExportSource {
    suspend fun latestVersion(type: ExportType): String

    suspend fun alliances(version: String): List<NewAlliance>

    suspend fun players(version: String): List<Player>
}
