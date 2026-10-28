package fr.nathanaudvard.emp.web.pages

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

// Never split over two lines: between a number and its unit, and the narrower one between groups of digits
const val NBSP = " "
const val DIGIT_GROUP_SEPARATOR = " "

val gameTimeZone = TimeZone.of("Europe/Paris")

private val weekdays = listOf("lun.", "mar.", "mer.", "jeu.", "ven.", "sam.", "dim.")

private val months =
    listOf("janv.", "févr.", "mars", "avr.", "mai", "juin", "juil.", "août", "sept.", "oct.", "nov.", "déc.")

private fun Instant.local(): LocalDateTime = toLocalDateTime(gameTimeZone)

private fun LocalDateTime.time(): String =
    "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

// When a computation was made: "mar. 27 oct. 18:00", "27 oct. 18:00" in narrow selectors, "mar. 27 oct. · 18:00"
// in the computation selector
fun formatDateTime(instant: Instant, weekday: Boolean = true, separator: String = " "): String = with(instant.local()) {
    val date = "$day$NBSP${months[month.ordinal]}$separator${time()}"
    if (weekday) "${weekdays[dayOfWeek.ordinal]} $date" else date
}

// The day only: "20 oct."
fun formatDay(instant: Instant): String = with(instant.local()) { "$day$NBSP${months[month.ordinal]}" }

private fun daysBetween(instant: Instant, now: Instant): Int = instant.local().date.daysUntil(now.local().date)

// When something was done, for what is left to do: "à l'instant", "il y a 20 min", "il y a 3 h", "hier", "il y a 3 j"
fun formatRelative(instant: Instant, now: Instant): String {
    val elapsed = now - instant
    val days = daysBetween(instant, now)
    return when {
        elapsed < 1.minutes -> "à l'instant"
        elapsed < 1.hours -> "il y a ${elapsed.inWholeMinutes}${NBSP}min"
        days == 0 || elapsed < 6.hours -> "il y a ${elapsed.inWholeHours}${NBSP}h"
        days == 1 -> "hier"
        else -> "il y a $days${NBSP}j"
    }
}

// The day only: "aujourd'hui", "hier", "il y a 3 j"
fun formatRelativeDay(instant: Instant, now: Instant): String = when (val days = daysBetween(instant, now)) {
    in Int.MIN_VALUE..0 -> "aujourd'hui"
    1 -> "hier"
    else -> "il y a $days${NBSP}j"
}

// A due time: "maintenant" once reached, "aujourd'hui 18:00", "demain 18:00", else the date
fun formatDue(instant: Instant, now: Instant): String {
    if (instant <= now) return "maintenant"
    return when (daysBetween(now, instant)) {
        0 -> "aujourd'hui ${instant.local().time()}"
        1 -> "demain ${instant.local().time()}"
        else -> formatDateTime(instant)
    }
}

fun formatArrival(instant: Instant): String = "arrive vers ${instant.local().time()}"

fun formatAmount(amount: Long): String {
    val grouped = amount.toString().removePrefix("-").reversed().chunked(3).joinToString(DIGIT_GROUP_SEPARATOR).reversed()
    return if (amount < 0) "−$grouped" else grouped
}

// Signed, with a dash for zero: positive is received, negative is given
fun formatBalance(balance: Long): String = when {
    balance > 0 -> "+${formatAmount(balance)}"
    balance < 0 -> formatAmount(balance)
    else -> "—"
}

// A whole number of hundredths, thousandths... as "7,84"
private fun formatDecimal(scaled: Long, decimals: Int): String {
    val scale = 10.0.pow(decimals).toLong()
    val whole = formatAmount(scaled / scale)
    if (decimals == 0) return whole
    // Between −1 and 0, the whole part carries no sign
    val sign = if (scaled < 0 && whole == "0") "−" else ""
    return "$sign$whole,${(abs(scaled) % scale).toString().padStart(decimals, '0')}"
}

// For narrow columns and tiles: "4,4 M", "1,63 M"
fun formatMillions(amount: Long, decimals: Int = 1): String =
    "${formatDecimal((amount / 1e6 * 10.0.pow(decimals)).roundToLong(), decimals)}${NBSP}M"

// "59 %" from 0.59, "7,84 %" from 0.0784 with two decimals
fun formatPercent(ratio: Double, decimals: Int = 0): String =
    "${formatDecimal((ratio * 100 * 10.0.pow(decimals)).roundToLong(), decimals)}${NBSP}%"

// What the convoys sent towards a delivery cover, null until one was sent
fun progressText(sent: Long, amount: Long): String? = when {
    sent <= 0 -> null
    sent >= amount -> "✓ envoyé"
    else -> "${formatAmount(sent)} envoyés, reste ${formatAmount(amount - sent)}"
}

// What the convoys of a giver cover, all recipients together
fun totalProgressText(sent: Long, amount: Long): String? =
    if (sent > 0 && sent >= amount) "✓ tout envoyé" else progressText(sent, amount)
