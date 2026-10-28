package fr.nathanaudvard.emp

import fr.nathanaudvard.emp.web.pages.DIGIT_GROUP_SEPARATOR
import fr.nathanaudvard.emp.web.pages.NBSP
import fr.nathanaudvard.emp.web.pages.formatAmount
import fr.nathanaudvard.emp.web.pages.formatArrival
import fr.nathanaudvard.emp.web.pages.formatBalance
import fr.nathanaudvard.emp.web.pages.formatDateTime
import fr.nathanaudvard.emp.web.pages.formatDue
import fr.nathanaudvard.emp.web.pages.formatMillions
import fr.nathanaudvard.emp.web.pages.formatPercent
import fr.nathanaudvard.emp.web.pages.formatRelative
import fr.nathanaudvard.emp.web.pages.formatRelativeDay
import fr.nathanaudvard.emp.web.pages.progressText
import fr.nathanaudvard.emp.web.pages.totalProgressText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class FormatsTest {
    private val g = DIGIT_GROUP_SEPARATOR
    private val u = NBSP

    private fun at(instant: String) = Instant.parse(instant)

    // Wednesday 28 October 2026, 10:12 in game time
    private val now = at("2026-10-28T09:12:00Z")

    @Test
    fun `dates of a computation are short and in game time`() {
        assertEquals("mar. 27${u}oct. 18:00", formatDateTime(at("2026-10-27T17:00:00Z")))
        assertEquals("27${u}oct. 18:00", formatDateTime(at("2026-10-27T17:00:00Z"), weekday = false))
        assertEquals("mer. 28${u}oct. 00:00", formatDateTime(at("2026-10-27T23:00:00Z")))
        assertEquals("ven. 1${u}janv. 00:30", formatDateTime(at("2026-12-31T23:30:00Z")))
        assertEquals("arrive vers 05:24", formatArrival(at("2026-10-28T04:24:00Z")))
    }

    @Test
    fun `dates to act on are relative`() {
        assertEquals("à l'instant", formatRelative(now - 30.seconds, now))
        assertEquals("il y a 20${u}min", formatRelative(now - 20.minutes, now))
        assertEquals("il y a 3${u}h", formatRelative(now - 3.hours, now))
        assertEquals("hier", formatRelative(at("2026-10-27T18:42:00Z"), now))
        assertEquals("il y a 3${u}j", formatRelative(now - 3.days, now))
        // Just before midnight is still a few hours ago
        assertEquals("il y a 2${u}h", formatRelative(at("2026-10-27T22:30:00Z"), at("2026-10-28T00:30:00Z")))
        assertEquals("hier", formatRelative(at("2026-12-31T08:00:00Z"), at("2027-01-01T08:00:00Z")))

        assertEquals("aujourd'hui", formatRelativeDay(now - 9.hours, now))
        assertEquals("hier", formatRelativeDay(now - 11.hours, now))
        assertEquals("il y a 5${u}j", formatRelativeDay(at("2026-12-27T12:00:00Z"), at("2027-01-01T12:00:00Z")))
    }

    @Test
    fun `due times are relative days`() {
        assertEquals("aujourd'hui 18:00", formatDue(at("2026-10-28T17:00:00Z"), now))
        assertEquals("demain 18:00", formatDue(at("2026-10-29T17:00:00Z"), now))
        assertEquals("demain 00:00", formatDue(at("2026-10-28T23:00:00Z"), now))
        assertEquals("maintenant", formatDue(now - 1.minutes, now))
        assertEquals("ven. 30${u}oct. 18:00", formatDue(at("2026-10-30T17:00:00Z"), now))
    }

    @Test
    fun `amounts are grouped by thousands`() {
        assertEquals("2${g}887${g}511", formatAmount(2_887_511))
        assertEquals("123${g}456", formatAmount(123_456))
        assertEquals("0", formatAmount(0))
        assertEquals("−1${g}234${g}567", formatAmount(-1_234_567))
        assertEquals("+1${g}234", formatBalance(1_234))
        assertEquals("−123${g}456", formatBalance(-123_456))
        assertEquals("—", formatBalance(0))
    }

    @Test
    fun `millions and percentages`() {
        assertEquals("4,4${u}M", formatMillions(4_400_000))
        assertEquals("78,1${u}M", formatMillions(78_149_999))
        assertEquals("1,0${u}M", formatMillions(999_999))
        assertEquals("1,63${u}M", formatMillions(1_634_000, decimals = 2))
        assertEquals("1${g}000,0${u}M", formatMillions(1_000_000_000))
        assertEquals("−2,5${u}M", formatMillions(-2_500_000))
        assertEquals("−0,4${u}M", formatMillions(-400_000))
        assertEquals("59${u}%", formatPercent(0.59))
        assertEquals("7,84${u}%", formatPercent(0.0784, decimals = 2))
        assertEquals("100${u}%", formatPercent(1.0))
    }

    @Test
    fun `what was sent`() {
        assertNull(progressText(0, 10))
        assertEquals("✓ envoyé", progressText(10, 10))
        assertEquals("2${g}000${g}000 envoyés, reste 2${g}915${g}921", progressText(2_000_000, 4_915_921))
        assertEquals("✓ tout envoyé", totalProgressText(12, 10))
        assertNull(totalProgressText(0, 10))
    }
}
