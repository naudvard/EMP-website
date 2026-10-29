package fr.nathanaudvard.emp

import fr.nathanaudvard.emp.service.ConvoyMessage
import fr.nathanaudvard.emp.service.parseConvoyMessages
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class ConvoyMessageTest {
    // 23:00 in the game: the convoys come back the next day
    private val evening = Instant.parse("2026-10-20T21:00:00Z")

    // The game's convoys page copied whole, with non-breaking spaces between thousands
    private val page = """
        Commerce · Convois en cours
        Vous allez livrer 120 000 nourritures et 0 materiaux à P3 dans 3H 12m 5s - Retour le 21 oct. à 05h24 - Annuler
        Vous allez livrer 0 nourritures et 2${' '}750${' '}216 materiaux à Crematogaster dans 10H 46m 4s - Retour le 21 oct. à 08h39 - Annuler
        Vous allez livrer 500 nourritures et 137 295 matériaux à ponera dans 2H - Retour le 21 oct. à 03h00 - Annuler
        Ressources disponibles : 12 000 000
    """.trimIndent()

    @Test
    fun `keeps the convoys of materials of a whole page`() {
        val convoys = parseConvoyMessages(page)

        assertEquals(listOf(ConvoyMessage("Crematogaster", 2_750_216), ConvoyMessage("ponera", 137_295)), convoys)
    }

    @Test
    fun `reads when the convoys arrive and come back once the instant is known`() {
        val (first, second) = parseConvoyMessages(page, evening)

        assertEquals(evening + 10.hours + 46.minutes + 4.seconds, first.arrivesAt)
        assertEquals(Instant.parse("2026-10-21T06:39:00Z"), first.returnAt)
        assertEquals(evening + 2.hours, second.arrivesAt)
        assertEquals(Instant.parse("2026-10-21T01:00:00Z"), second.returnAt)
    }

    @Test
    fun `a return in the new year is in the next year`() {
        val newYearsEve = Instant.parse("2026-12-31T20:00:00Z")

        val (convoy) = parseConvoyMessages(
            "Vous allez livrer 0 nourritures et 1 000 materiaux à P3 dans 1J 2H - Retour le 2 janv. à 00h15 - Annuler",
            newYearsEve,
        )

        assertEquals(newYearsEve + 26.hours, convoy.arrivesAt)
        assertEquals(Instant.parse("2027-01-01T23:15:00Z"), convoy.returnAt)
    }

    @Test
    fun `a line without its times is still a convoy`() {
        val convoys = parseConvoyMessages("Vous allez livrer 0 nourritures et 1 000 materiaux à P3 dans - Annuler", evening)

        assertEquals(listOf(ConvoyMessage("P3", 1_000)), convoys)
    }
}
