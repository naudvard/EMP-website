package fr.nathanaudvard.emp.domain.port

import fr.nathanaudvard.emp.domain.model.Alliance
import fr.nathanaudvard.emp.domain.model.NewAlliance

interface AllianceRepository {
    suspend fun saveVersion(version: String, alliances: List<NewAlliance>): List<Alliance>
}
