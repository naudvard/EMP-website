package fr.nathanaudvard.emp.domain.model

import fr.nathanaudvard.emp.domain.exception.InvalidVersionException
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime

enum class ExportType {
    PLAYERS,
    ALLIANCES,
}

sealed interface ExportVersion {
    data object Latest : ExportVersion

    data class Exact(val value: String) : ExportVersion

    companion object {
        private val exactVersion = Regex("""\d{12}""")

        fun parse(version: String): ExportVersion = when {
            version == "latest" -> Latest
            exactVersion.matches(version) -> Exact(version)
            else -> throw InvalidVersionException("Export version must be \"latest\" or a yyyyMMddHHmm date, got $version")
        }

        fun of(instant: Instant): String = with(instant.toLocalDateTime(TimeZone.UTC)) {
            listOf(year, month.number, day, hour, minute)
                .joinToString("") { it.toString().padStart(2, '0') }
        }
    }
}
