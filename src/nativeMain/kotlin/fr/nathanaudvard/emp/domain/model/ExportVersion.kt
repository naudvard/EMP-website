package fr.nathanaudvard.emp.domain.model

import fr.nathanaudvard.emp.domain.exception.InvalidVersionException
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

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
            exactVersion.matches(version) && isDate(version) -> Exact(version)
            else -> throw InvalidVersionException("Export version must be \"latest\" or a yyyyMMddHHmm date, got $version")
        }

        // Rejects impossible dates such as February 30th before asking Fourmizzz
        private fun isDate(version: String): Boolean = runCatching {
            val (year, month, day, hour, minute) =
                listOf(0..3, 4..5, 6..7, 8..9, 10..11).map { version.substring(it).toInt() }
            LocalDateTime(year, month, day, hour, minute)
        }.isSuccess

        fun of(instant: Instant): String = with(instant.toLocalDateTime(TimeZone.UTC)) {
            listOf(year, month.number, day, hour, minute)
                .joinToString("") { it.toString().padStart(2, '0') }
        }

        fun instant(version: String): Instant {
            val (year, month, day, hour, minute) =
                listOf(0..3, 4..5, 6..7, 8..9, 10..11).map { version.substring(it).toInt() }
            return LocalDateTime(year, month, day, hour, minute).toInstant(TimeZone.UTC)
        }
    }
}
