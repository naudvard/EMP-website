package fr.nathanaudvard.emp

import com.fleeksoft.ksoup.Ksoup
import fr.nathanaudvard.emp.api.resources.Alliances
import fr.nathanaudvard.emp.api.resources.Exports
import fr.nathanaudvard.emp.fourmizzz.PlayerRes
import fr.nathanaudvard.emp.web.pages.DIGIT_GROUP_SEPARATOR
import fr.nathanaudvard.emp.web.pages.NBSP
import fr.nathanaudvard.emp.web.resources.Me
import io.ktor.client.plugins.resources.get
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

class MePageTest {
    // P1 and P2 deliver to P3 and P4: P1 78 000 to P3 and 12 000 to P4, P2 42 000 to P4
    private val players = mapOf(1L to 4_000L, 2L to 3_000L, 3L to 500L, 4L to 1_000L)
        .map { (id, field) -> fourmi.copy(id = id, pseudo = "P$id", field = field) }

    private suspend fun AppTestScope.compute(
        version: String = "202610201200",
        members: List<PlayerRes> = players,
        before: suspend () -> Unit = {},
    ) {
        fourmizzz.players(version, members)
        sync(Exports.Players.Sync())
        before()
        post(Alliances.Tag.Wages(Alliances.Tag(tag = "EMP"), distribution = "minimal"))
    }

    private suspend fun AppTestScope.computeOnce(
        viewer: String,
        members: List<PlayerRes> = players,
        before: suspend () -> Unit = {},
    ) {
        compute(members = members, before = before)
        signIn(viewer, chief = false)
    }

    // Yesterday's computation then today's, with the same deliveries
    private suspend fun AppTestScope.computeTwice(viewer: String) {
        clock.now = NOW - 24.hours
        compute("202610191200")
        clock.now = NOW
        compute()
        signIn(viewer, chief = false)
    }

    private suspend fun AppTestScope.page(): String = flat(client.get(Me()).bodyAsText())

    // Digit groups are read with plain spaces
    private fun flat(html: String) = html.replace(Regex(">\\s+<"), "><").replace(DIGIT_GROUP_SEPARATOR, " ")

    private fun section(page: String, id: String): String =
        Regex("""<section[^>]* id="$id"[^>]*>(.*?)</section>""", RegexOption.DOT_MATCHES_ALL).find(page)?.groupValues?.get(1) ?: ""

    // Recipient, amount and state of each delivery of a card
    private fun deliveries(section: String) =
        Ksoup.parse(section).select("tr.me-deliveries__row").map { row ->
            listOf(
                row.selectFirst(".me-deliveries__pseudo")!!.text(),
                row.selectFirst(".me-deliveries__amount > .amount")!!.text(),
                row.selectFirst(".me-deliveries__state")!!.text(),
            )
        }

    private suspend fun AppTestScope.latestId() = wageHistory().bounds.latestId!!

    private suspend fun AppTestScope.declare(computationId: Long, to: Long): HttpResponse =
        client.submitForm("/me/computations/$computationId/deliveries/$to/sent", parameters { }) { htmx() }

    private suspend fun AppTestScope.paste(text: String): HttpResponse =
        client.submitForm("/me/convoys", parameters { append("message", text) }) { htmx() }

    private suspend fun AppTestScope.deleteConvoys(ids: List<Long>): HttpResponse =
        client.submitForm("/me/convoys/delete", parameters {
            ids.forEach { append("id", it.toString()) }
        }) { htmx() }

    private suspend fun AppTestScope.level(kind: String, vararg values: Pair<String, String>): HttpResponse =
        client.submitForm("/me/levels/$kind", parameters { values.forEach { (name, value) -> append(name, value) } }) { htmx() }

    private suspend fun AppTestScope.convoys() =
        query("SELECT from_player_id, to_player_id, amount, source, declared_by, arrives_at, return_at FROM convoys ORDER BY id") { cursor ->
            listOf(cursor.getLong(0), cursor.getLong(1), cursor.getLong(2), cursor.getString(3), cursor.getLong(4), cursor.getString(5), cursor.getString(6))
        }

    private fun line(amount: String, recipient: String, rest: String = "dans 2H 30m - Retour le 20 oct. à 19h30") =
        "Vous allez livrer 0 nourritures et $amount materiaux à $recipient $rest - Annuler"

    @Test
    fun `asks for the levels first while there is no computation`() = appTest {
        syncAndSignIn()

        val page = page()

        assertTrue("""<h1 class="page-title">Fourmi</h1>""" in page)
        assertTrue("Premier calcul aujourd'hui 18:00" in section(page, "state"))
        assertTrue("ils comptent dès ce calcul" in section(page, "state"))
        assertTrue("""class="emp-card me-levels me-levels--first" id="levels"""" in page)
        assertFalse("Mon calcul" in page)
        assertTrue("""<a href="/me" class="emp-tab" aria-current="page">Moi</a>""" in page)
    }

    @Test
    fun `lists what is left to send and declares the rest of a delivery`() = appTest {
        computeOnce("P1")
        val id = latestId()

        val before = section(page(), "send")
        assertTrue("""<h2 class="me-card-caption" id="send-title">À envoyer avant 18:00</h2><strong class="figure amount">90 000<span class="me-figure-unit"> bois<span class="me-figure-unit__wide"> sur 90 000, à 2 joueurs</span></span></strong>""" in before)
        assertTrue("sur 90 000" in before)
        assertEquals(listOf(listOf("→ P3", "78 000", "J'ai envoyé"), listOf("→ P4", "12 000", "J'ai envoyé")), deliveries(before))
        assertTrue("""aria-label="J'ai envoyé à P3"""" in before)

        val res = declare(id, 3)

        assertEquals(HttpStatusCode.OK, res.status)
        assertEquals(listOf(listOf(1L, 3L, 78_000L, "declared", 1L, null, null)), convoys())
        val after = section(page(), "send")
        assertEquals(listOf(listOf("→ P3", "78 000", "✓ 78 000 envoyé à l'instant Annuler"), listOf("→ P4", "12 000", "J'ai envoyé")), deliveries(after))
        assertTrue(""">12 000<span class="me-figure-unit"> bois<span""" in after)
        // Nothing is left: declaring again adds nothing
        declare(id, 3)
        assertEquals(1, convoys().size)

        val convoyId = query("SELECT id FROM convoys") { it.getLong(0)!! }.single()
        deleteConvoys(listOf(convoyId))
        assertTrue(convoys().isEmpty())

        declare(id, 3)
        declare(id, 4)
        assertTrue("""<h2 class="me-card-caption" id="send-title">✓ Tout envoyé pour ce calcul</h2>""" in page())
    }

    @Test
    fun `declaring after a partial convoy sends only what is left`() = appTest {
        computeOnce("P1")
        paste(line("50 000", "P3"))

        val partial = deliveries(section(page(), "send"))
        assertEquals(listOf("→ P3", "28 000", "J'ai envoyé"), partial.first())
        assertTrue("50 000 déjà envoyés" in page())

        declare(latestId(), 3)

        assertEquals(listOf(50_000L, 28_000L), convoys().map { it[2] })
    }

    @Test
    fun `records the convoys of a pasted page and ignores them when pasted again`() = appTest {
        computeOnce("P1")
        val text = """
            Convois en cours
            Vous allez livrer 120 000 nourritures et 0 materiaux à P3 dans 1H - Retour le 20 oct. à 16h30 - Annuler
            Vous allez livrer 0 nourritures et 78${' '}000 matériaux à p3 dans 10H 46m 4s - Retour le 21 oct. à 03h49 - Annuler
            Vous allez livrer 0 nourritures et 2 000 materiaux à P4 dans 2H 30m - Retour le 20 oct. à 19h30 - Annuler
            Vous allez livrer 0 nourritures et 300 000 materiaux à P2 dans 1H - Retour le 20 oct. à 16h30 - Annuler
        """.trimIndent()

        val first = paste(text)

        assertEquals(HttpStatusCode.OK, first.status)
        val page = flat(first.bodyAsText())
        assertTrue("<strong>2 convois enregistrés</strong><span>78 000 → P3, arrive vers 01:16</span><span>2 000 → P4, arrive vers 17:00</span>" in page)
        assertTrue("Annuler ces 2 convois" in page)
        assertTrue("<strong>1 convoi non enregistré</strong><span>300 000 → P2 : aucun envoi prévu vers P2 dans ton calcul. Vérifie le destinataire dans le jeu, ou préviens un chef.</span>" in page)
        assertEquals(
            listOf(
                listOf(1L, 3L, 78_000L, "pasted", 1L, "2026-10-20T23:16:04Z", "2026-10-21T01:49:00Z"),
                listOf(1L, 4L, 2_000L, "pasted", 1L, "2026-10-20T15:00:00Z", "2026-10-20T17:30:00Z"),
            ),
            convoys(),
        )

        val again = flat(paste(text).bodyAsText())

        assertTrue("<strong>Rien de nouveau</strong>" in again)
        assertTrue("78 000 → P3 est déjà déclaré (aujourd'hui 14:30)." in again)
        assertEquals(2, convoys().size)
    }

    @Test
    fun `a declared convoy found in a pasted message is not recorded twice and gets its times`() = appTest {
        computeOnce("P1")
        declare(latestId(), 4)

        val page = flat(paste(line("12 000", "P4")).bodyAsText())

        assertTrue("12 000 → P4 est déjà déclaré" in page)
        assertEquals(listOf(listOf(1L, 4L, 12_000L, "declared", 1L, "2026-10-20T15:00:00Z", "2026-10-20T17:30:00Z")), convoys())
    }

    @Test
    fun `says when the text holds no convoy of materials and keeps it`() = appTest {
        computeOnce("P1")

        val res = paste("Vous allez livrer 120 000 nourritures et 0 materiaux à P3 dans 1H - Annuler")

        assertEquals(HttpStatusCode.BadRequest, res.status)
        val page = flat(res.bodyAsText())
        assertTrue("<strong>Aucun convoi de matériaux dans ce texte</strong>" in page)
        assertTrue("""class="paste paste--manual"""" in page)
        assertTrue("à P3 dans 1H - Annuler</textarea>" in page)
        assertTrue(convoys().isEmpty())
    }

    @Test
    fun `cancels the convoys just pasted and lists those of the computation`() = appTest {
        computeOnce("P1")
        paste(line("1 000", "P3") + "\n" + line("2 000", "P4"))

        val convoysCard = section(page(), "convoys")
        assertTrue("→ P4 · <span class=\"amount\">2 000</span>" in convoysCard)
        assertTrue("collé mar. 20${NBSP}oct. 14:30 · arrive vers 17:00" in convoysCard)
        assertTrue("""aria-label="Supprimer le convoi de 2 000 à P4"""" in convoysCard)

        val ids = query("SELECT id FROM convoys") { it.getLong(0)!! }
        deleteConvoys(ids)

        assertTrue(convoys().isEmpty())
        assertTrue("Aucun convoi enregistré pour ce calcul." in page())
    }

    @Test
    fun `a member removes only their own convoys`() = appTest {
        computeOnce("P1")
        paste(line("1 000", "P3"))
        val id = query("SELECT id FROM convoys") { it.getLong(0)!! }.single()

        signIn("P2", chief = true)
        deleteConvoys(listOf(id))

        assertEquals(1, convoys().size)
    }

    @Test
    fun `shows the receiver who sent and when it arrives`() = appTest {
        computeOnce("P1")
        paste(line("12 000", "P4"))
        signIn("P4", chief = false)

        val page = page()
        val card = section(page, "receive")

        assertTrue("""<h2 class="me-card-caption" id="receive-title">Tu reçois</h2>""" in card)
        assertTrue("""<strong class="figure amount balance--positive">54 000</strong><span class="text-secondary">bois de 2 joueurs</span>""" in card)
        assertTrue("reste 42 000" in card)
        assertTrue("""<span class="emp-row__title">P2</span><span class="amount">42 000</span></div><span class="small text-secondary">pas encore envoyé</span>""" in card)
        assertTrue("""<span class="emp-row__title text-secondary">P1</span>""" in card)
        assertTrue("Envoyé</span><span class=\"caption text-secondary\">arrive vers 17:00</span>" in card)
        assertTrue("Rien à faire de ton côté : chaque livreur enregistre ses convois." in card)
        assertFalse("<button" in card)
        assertFalse("id=\"send\"" in page)
    }

    @Test
    fun `saves a level at each step and undoes it`() = appTest {
        computeOnce("P1")

        val res = level("INCUBATOR", "level" to "1")

        assertEquals(HttpStatusCode.OK, res.status)
        val page = flat(res.bodyAsText())
        assertTrue("✓ Couveuse 0 → 1 enregistré" in page)
        assertTrue("""<form action="/me/levels/INCUBATOR" method="post" class="emp-level emp-level--changed"""" in page)
        assertTrue("mis à jour à l'instant" in page)
        assertTrue("""<span class="me-shortcut__levels">1 · 0 · 0 · 0</span>""" in page)
        assertTrue("""aria-label="Couveuse, un niveau de plus"""" in page)
        assertTrue(Regex("""<button[^>]*emp-stepper__plus[^>]*>""").findAll(page).any { """value="2"""" in it.value })

        level("INCUBATOR", "typed" to "12")
        level("INCUBATOR", "level" to "41")
        level("SOLARIUM", "typed" to "abc")
        level("UNKNOWN", "level" to "3")
        assertEquals(
            listOf(listOf(12L, null, 1L)),
            query("SELECT incubator_level, solarium_level, levels_updated_by FROM players WHERE id = 1") { c -> (0..2).map { c.getLong(it) } },
        )

        val change = query("SELECT max(id) FROM player_level_changes") { it.getLong(0)!! }.single()
        client.submitForm("/me/levels/changes/$change/undo", parameters { }) { htmx() }
        assertEquals(listOf(1L), query("SELECT incubator_level FROM players WHERE id = 1") { it.getLong(0)!! })
        assertFalse("enregistré</span>" in page())
    }

    @Test
    fun `asks whether old levels are still up to date until confirmed`() = appTest {
        computeOnce("P1")
        level("SOLARIUM", "level" to "1")
        clock.now = NOW + 8.days

        val stale = section(page(), "levels")
        assertTrue("mis à jour il y a 8${NBSP}j" in stale)
        assertTrue("Toujours à jour ?" in stale)

        client.submitForm("/me/levels/confirm", parameters { }) { htmx() }

        val confirmed = section(page(), "levels")
        assertFalse("Toujours à jour ?" in confirmed)
        assertTrue("mis à jour à l'instant" in confirmed)
        assertEquals(listOf(listOf(0L, 1L)), query("SELECT coalesce(incubator_level, 0), solarium_level FROM players WHERE id = 1") { c -> (0..1).map { c.getLong(it) } })
    }

    // Figure of each step of the detail, by title
    private fun steps(page: String) =
        Regex("""<div class="me-step__head"><strong>([^<]*)</strong><span class="me-step__figure amount[^"]*">([^<]*)</span></div><span class="small text-secondary">([^<]*)</span>""")
            .findAll(page).associate { it.groupValues[1] to (it.groupValues[2] to it.groupValues[3]) }

    private fun number(text: String) = text.substringBefore(" de").replace("−", "-").filter { it.isDigit() || it == '-' }.toLong()

    @Test
    fun `explains the balance step by step with figures that add up`() = appTest {
        // P3 is a colony of P4: 20 % of its production goes to P4
        computeOnce("P4", players.map { if (it.id == 3L) it.copy(masterPlayerId = 4) else it })

        val master = steps(section(page(), "calculation"))
        assertEquals(listOf("Ta production", "Ta colonie", "Ton besoin", "Ta part de la récolte", "Solde"), master.keys.toList())
        assertEquals("48 000", master.getValue("Ta production").first)
        assertTrue(master.getValue("Ta production").second.startsWith("TdC moyen 1 000 cm² × 48 bois par jour × 1${NBSP}jour (1 mesure"))
        assertEquals("+4 800" to "P3 te reverse 20${NBSP}% (20${NBSP}% + ton étable niv. 0). Production propre : 52 800", master.getValue("Ta colonie"))
        assertEquals("25,00${NBSP}% de l'alliance", master.getValue("Ton besoin").first)
        assertEquals("102 000" to "25,00${NBSP}% × 408 000 bois récoltés par l'alliance", master.getValue("Ta part de la récolte"))
        assertEquals("+49 200", master.getValue("Solde").first)
        val own = number(master.getValue("Ta production").first) + number(master.getValue("Ta colonie").first)
        assertEquals(number(master.getValue("Ta part de la récolte").first) - own, number(master.getValue("Solde").first))

        signIn("P3", chief = false)
        val colony = steps(section(page(), "calculation"))
        assertEquals("−4 800" to "Tu es une colonie de P4 : 20${NBSP}% de ta production lui reviennent. Production propre : 19 200", colony.getValue("Part de ton maître"))
        assertTrue("""<a href="/wages#method" class="me-method-link">Méthode et totaux de l'alliance →</a>""" in page())
    }

    @Test
    fun `shows what a debtor keeps and what the receivers miss when the chiefs let them accumulate`() = appTest {
        computeOnce("P2") { execute("UPDATE players SET wage_settlement = 'ACCUMULATE_DEBT' WHERE id = 2") }

        val page = page()
        val state = section(page, "state")
        assertTrue("Aujourd'hui, rien à envoyer" in state)
        assertTrue("""Les chefs ont choisi que tu gardes ta part : <strong class="amount">42 000</strong> s'ajoutent à ta dette.""" in state)
        assertTrue("""<span class="me-state__label">Dette</span><strong class="amount">42 000</strong>""" in state)
        assertTrue("Solde négatif : les chefs ont choisi que tu gardes ta part, elle s'ajoute à ta dette." in page)
        assertFalse("id=\"convoys\"" in page)

        signIn("P4", chief = false)
        val receiver = steps(section(page(), "calculation"))
        assertEquals("+54 000", receiver.getValue("Solde").first)
        assertEquals(
            "36 818" to "−17 182 : un livreur cumule sa dette aujourd'hui, le manque est réparti entre les receveurs. Quand il remboursera, ce bois ira aux receveurs du jour.",
            receiver.getValue("À recevoir"),
        )
    }

    @Test
    fun `says when there is nothing to send nor receive`() = appTest {
        syncAndSignIn()
        post(Alliances.Tag.Wages(Alliances.Tag(tag = "EMP"), distribution = "minimal"))

        val page = page()

        assertTrue("Rien à faire aujourd'hui" in section(page, "state"))
        assertTrue("me-levels--first" in page)
        assertTrue("Solde nul : ta production couvre exactement ta part." in page)
    }

    @Test
    fun `welcomes a member missing from the latest computation`() = appTest {
        compute()
        fourmizzz.players("202610201300", players + fourmi.copy(id = 5, pseudo = "P5"))
        sync(Exports.Players.Sync(version = "202610201300"))
        signIn("P5", chief = false)

        val page = page()

        assertTrue("Bienvenue, P5" in page)
        assertTrue("Tu n'étais pas encore dans le calcul du 20${NBSP}oct. Tu seras dans celui de 18:00 : remplis tes niveaux d'ici là." in page)
        assertFalse("Mon calcul" in page)
        assertFalse("nouveau" in page)
    }

    @Test
    fun `keeps what is left of the previous computation to send`() = appTest {
        computeTwice("P1")
        val previousId = wageHistory().olderId!!

        val card = section(page(), "previous")
        assertTrue("Encore à envoyer du calcul d'hier" in card)
        assertEquals(listOf(listOf("→ P3", "78 000", "J'ai envoyé"), listOf("→ P4", "12 000", "J'ai envoyé")), deliveries(card))
        assertTrue("""aria-label="J'ai envoyé à P3, calcul d'hier"""" in card)

        declare(previousId, 3)
        // A pasted convoy goes to today's computation first
        paste(line("12 000", "P4"))
        assertEquals(listOf(previousId, latestId()), query("SELECT computation_id FROM convoys ORDER BY id") { it.getLong(0)!! })
        declare(previousId, 4)

        // Sent since today's computation: still shown, to be undone, and listed with the convoys
        val page = page()
        val sent = section(page, "previous")
        assertTrue("✓ Tout envoyé du calcul d'hier" in sent)
        assertEquals(listOf(listOf("→ P3", "78 000", "✓ 78 000 envoyé à l'instant Annuler"), listOf("→ P4", "12 000", "✓ 12 000 envoyé à l'instant Annuler")), deliveries(sent))
        assertTrue("· calcul d'hier" in section(page, "convoys"))
    }

    @Test
    fun `points out a computation the player has not seen yet`() = appTest {
        clock.now = NOW - 24.hours
        compute("202610191200")
        signIn("P1", chief = false)

        assertTrue("""<span class="emp-badge emp-badge--solid">nouveau</span>""" in page())
        assertFalse("nouveau" in page())

        clock.now = NOW
        compute()

        assertTrue("nouveau" in page())
    }

    @Test
    fun `the forms that change a part of the page tell htmx which ones`() = appTest {
        computeOnce("P1")

        val page = page()

        assertTrue("""<form action="/me/levels/INCUBATOR" method="post" class="emp-level" hx-post="/me/levels/INCUBATOR" hx-swap="none" hx-select-oob="#levels,#levels-shortcut">""" in page)
        assertTrue("""<form action="/me/computations/${latestId()}/deliveries/3/sent" method="post" hx-post="/me/computations/${latestId()}/deliveries/3/sent" hx-swap="none" hx-select-oob="#sending">""" in page)
        assertTrue("""<div class="me__column me__column--main" id="sending">""" in page)
    }

    @Test
    fun `htmx gets the levels and their shortcut alone`() = appTest {
        computeOnce("P1")

        val res = client.submitForm("/me/levels/INCUBATOR", parameters { append("level", "1") }) { htmx() }

        assertEquals(HttpStatusCode.OK, res.status)
        val sections = flat(res.bodyAsText())
        assertEquals(listOf("levels-shortcut", "levels"), sectionIds(sections))
        assertTrue("✓ Couveuse 0 → 1 enregistré" in sections)
        assertTrue("""<span class="me-shortcut__levels">1 · 0 · 0 · 0</span>""" in sections)
        assertTrue("""<button name="level" type="submit" class="emp-stepper__plus" value="2" id="level-incubator-plus"""" in sections)
        assertEquals(listOf(1L), query("SELECT incubator_level FROM players WHERE id = 1") { it.getLong(0)!! })
    }

    @Test
    fun `htmx gets the sending column after a declaration or a paste`() = appTest {
        computeOnce("P1")

        val declared = client.submitForm("/me/computations/${latestId()}/deliveries/3/sent", parameters { }) { htmx() }

        assertEquals(HttpStatusCode.OK, declared.status)
        val column = flat(declared.bodyAsText())
        assertEquals(listOf("sending"), sectionIds(column))
        assertEquals(listOf(listOf("→ P3", "78 000", "✓ 78 000 envoyé à l'instant Annuler"), listOf("→ P4", "12 000", "J'ai envoyé")), deliveries(column))
        assertTrue("""id="convoys"""" in column)

        val refused = client.submitForm("/me/convoys", parameters { append("message", "Rien à livrer") }) { htmx() }

        assertEquals(HttpStatusCode.BadRequest, refused.status)
        assertEquals(listOf("sending"), sectionIds(refused.bodyAsText()))
        assertTrue("<strong>Aucun convoi de matériaux dans ce texte</strong>" in refused.bodyAsText())
    }

    @Test
    fun `htmx is sent to the access page when the session is gone`() = appTest {
        syncAndSignIn()
        signOut()

        val res = client.submitForm("/me/levels/confirm", parameters { }) { htmx() }

        assertEquals(HttpStatusCode.NoContent, res.status)
        assertEquals("/access", res.headers["HX-Redirect"])
    }

    @Test
    fun `the scripts are served for good with htmx first`() = appTest {
        syncAndSignIn()
        val sources = Regex("""<script src="(/assets/([a-z]+)-[0-9a-f]{10}\.js)"[^>]* defer""").findAll(page()).toList()

        assertEquals(listOf("htmx", "emp"), sources.map { it.groupValues[2] })
        val scripts = sources.map { client.get(it.groupValues[1]) }
        scripts.forEach {
            assertEquals(HttpStatusCode.OK, it.status)
            assertEquals("public, max-age=31536000, immutable", it.headers[HttpHeaders.CacheControl])
        }
        assertTrue("version:\"2.0.11\"" in scripts[0].bodyAsText())
        assertTrue("htmx:responseError" in scripts[1].bodyAsText())
    }

    @Test
    fun `a browser without JavaScript is only asked to turn it on`() = appTest {
        syncAndSignIn()

        assertTrue("""<body><noscript><main class="emp-page"><div class="emp-message emp-message--warning" role="status">Active JavaScript pour utiliser le site de l'alliance.</div></main></noscript><header""" in page())
    }
}
