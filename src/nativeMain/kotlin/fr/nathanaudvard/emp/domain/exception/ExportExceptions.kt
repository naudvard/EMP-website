package fr.nathanaudvard.emp.domain.exception

import fr.nathanaudvard.emp.domain.model.ExportType

class InvalidVersionException(message: String) : Exception(message)

class ExportNotFoundException(type: ExportType, version: String) :
    Exception("No ${type.name.lowercase()} export for version $version")

class ExportUnavailableException(message: String, cause: Throwable? = null) : Exception(message, cause)
