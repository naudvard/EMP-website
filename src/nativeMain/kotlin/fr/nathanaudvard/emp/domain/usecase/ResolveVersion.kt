package fr.nathanaudvard.emp.domain.usecase

import fr.nathanaudvard.emp.domain.model.ExportType
import fr.nathanaudvard.emp.domain.model.ExportVersion
import fr.nathanaudvard.emp.fourmizzz.FourmizzzClient

// "latest" must be resolved: the stored version has to be the real (UTC) export version
internal suspend fun FourmizzzClient.resolve(type: ExportType, version: ExportVersion): String = when (version) {
    ExportVersion.Latest -> latestVersion(type)
    is ExportVersion.Exact -> version.value
}
