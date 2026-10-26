package fr.nathanaudvard.emp.domain.exception

import fr.nathanaudvard.emp.domain.model.ExportType

class InvalidVersionException(message: String) : Exception(message)

class ExportNotFoundException(message: String) : Exception(message) {
    constructor(
        type: ExportType,
        version: String
    ) : this("No ${type.name.lowercase()} export for version $version")
}

class AllianceNotFoundException(tag: String) :
    Exception("No member of alliance $tag in the latest players export")

class ExportUnavailableException(message: String, cause: Throwable? = null) :
    Exception(message, cause)
