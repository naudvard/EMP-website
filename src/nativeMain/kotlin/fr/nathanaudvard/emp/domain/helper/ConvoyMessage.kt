package fr.nathanaudvard.emp.domain.helper

import fr.nathanaudvard.emp.domain.model.gameTimeZone
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

// What the game says of a launched convoy, as
// "Vous allez livrer 0 nourritures et 200 000 materiaux à Alcaz dans 10H 46m 4s - Retour le 27 oct. à 08h39 - Annuler".
// Its times are only known when the message is read at a given instant
data class ConvoyMessage(
    val recipient: String,
    val materials: Long,
    val arrivesAt: Instant? = null,
    val returnAt: Instant? = null,
)

// Thousands may be separated by any space, non-breaking ones included when copied from the game
private const val NUMBER = """(\d[\d\s  .]*)"""

private val CONVOY = Regex(
    """livrer\s+$NUMBER\s+nourritures?\s+et\s+$NUMBER\s+mat[ée]riaux\s+[àa]\s+(\S+)\s+dans\s*((?:\d+\s*[jhms]\s*)*)""" +
        """(?:[\s\-–]*Retour\s+le\s+(\d{1,2})\s+([^\s\d.]+)\.?\s+[àa]\s+(\d{1,2})\s*h\s*(\d{2}))?""",
    RegexOption.IGNORE_CASE,
)

private val DURATION_PART = Regex("""(\d+)\s*([jhms])""", RegexOption.IGNORE_CASE)

// Every convoy of the text: one line or the whole convoys page of the game. Convoys of food only carry no wage
fun parseConvoyMessages(text: String, now: Instant? = null): List<ConvoyMessage> =
    CONVOY.findAll(text).mapNotNull { match ->
        val (_, materials, recipient, duration, day, month, hour, minute) = match.destructured
        val amount = materials.filter(Char::isDigit).toLongOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
        ConvoyMessage(
            recipient = recipient,
            materials = amount,
            arrivesAt = now?.let { parseDuration(duration)?.let { now + it } },
            returnAt = now?.let { returnTime(day, month, hour, minute, now) },
        )
    }.toList()

private fun parseDuration(text: String): Duration? = DURATION_PART.findAll(text).map { part ->
    val value = part.groupValues[1].toLong()
    when (part.groupValues[2].lowercase()) {
        "j" -> value.days
        "h" -> value.hours
        "m" -> value.minutes
        else -> value.seconds
    }
}.reduceOrNull(Duration::plus)

// "janv.", "févr.", "fevr", "août", "aout"...: told apart by their first letters
private fun monthNumber(name: String): Int? = with(name.lowercase()) {
    when {
        startsWith("ja") -> 1
        startsWith("f") -> 2
        startsWith("mar") -> 3
        startsWith("av") -> 4
        startsWith("mai") -> 5
        startsWith("juin") -> 6
        startsWith("juil") -> 7
        startsWith("ao") -> 8
        startsWith("s") -> 9
        startsWith("o") -> 10
        startsWith("n") -> 11
        startsWith("d") -> 12
        else -> null
    }
}

// The message gives no year: the one that puts the return closest to now, on the eve of a new year as well
private fun returnTime(day: String, month: String, hour: String, minute: String, now: Instant): Instant? {
    if (day.isEmpty()) return null
    val monthNumber = monthNumber(month) ?: return null
    val year = now.toLocalDateTime(gameTimeZone).year
    return (year - 1..year + 1).mapNotNull { candidate ->
        runCatching {
            LocalDateTime(candidate, monthNumber, day.toInt(), hour.toInt(), minute.toInt()).toInstant(gameTimeZone)
        }.getOrNull()
    }.minByOrNull { (it - now).absoluteValue }
}
