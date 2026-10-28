package fr.nathanaudvard.emp

import fr.nathanaudvard.emp.api.resources.Exports
import fr.nathanaudvard.emp.config.SESSION_COOKIE
import fr.nathanaudvard.emp.fourmizzz.PlayerRes
import fr.nathanaudvard.emp.service.AccessService
import fr.nathanaudvard.emp.web.pages.NBSP
import fr.nathanaudvard.emp.web.resources.Access
import fr.nathanaudvard.emp.web.resources.Home
import io.ktor.client.plugins.resources.get
import io.ktor.client.plugins.resources.post
import io.ktor.client.request.accept
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import io.ktor.http.setCookie
import io.ktor.server.plugins.di.dependencies
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class AccessTest {
    private val abeille = fourmi.copy(id = 124, pseudo = "abeille")

    // Fourmi is a chief, abeille a member
    private suspend fun AppTestScope.syncMembers(players: List<PlayerRes> = listOf(fourmi, abeille)) {
        fourmizzz.players("202610201200", players)
        sync(Exports.Players.Sync(version = "202610201200"))
    }

    private val AppTestScope.browser get() = client.config { followRedirects = false }

    // The text of the message the chief copies, the script asking for it alone
    private suspend fun AppTestScope.copyMessage(playerId: Long): String =
        client.post(Access.Links.Message(playerId = playerId)) { accept(ContentType.Text.Plain) }.bodyAsText()

    private fun link(message: String) = Regex("""http://localhost(/access/[0-9a-f]+)""").find(message)!!.groupValues[1]

    // Opens the link in a browser without a session, and keeps the session it opened
    private suspend fun AppTestScope.open(path: String): HttpResponse {
        signOut()
        val res = browser.get(path)
        res.setCookie().find { it.name == SESSION_COOKIE }?.let { identity.session = it.value }
        return res
    }

    private suspend fun AppTestScope.showDeviceCode(): String {
        val page = client.post(Access.Device()).bodyAsText()
        return Regex("""<strong class="device-code__value">([^<]+)</strong>""").find(page)!!.groupValues[1]
    }

    private suspend fun AppTestScope.typeCode(code: String): HttpResponse =
        browser.submitForm("/access/code", parameters { append("code", code) })

    @Test
    fun `opening a link once keeps the browser signed in for a year`() = appTest {
        syncMembers()
        val access: AccessService = application.dependencies.resolve()
        val token = access.issueLink(fourmi.id, createdBy = null, clock.now)

        val res = open("/access/$token")

        assertEquals(HttpStatusCode.Found, res.status)
        assertEquals("/", res.headers[HttpHeaders.Location])
        val cookie = res.setCookie().single { it.name == SESSION_COOKIE }
        assertEquals(31_536_000, cookie.maxAge)
        assertEquals("/", cookie.path)
        assertTrue(cookie.secure)
        assertTrue(cookie.httpOnly)
        assertEquals("Lax", cookie.extensions["SameSite"])
        assertEquals(64, cookie.value.length)

        val home = browser.get(Home())
        assertEquals(HttpStatusCode.OK, home.status)
        assertEquals(cookie.value, home.setCookie().single { it.name == SESSION_COOKIE }.value)
        // Only hashes are stored
        assertTrue(query("SELECT token_hash FROM access_links") { it.getString(0)!! }.none { it == token })
        assertTrue(query("SELECT id_hash FROM sessions") { it.getString(0)!! }.none { it == cookie.value })
        assertEquals(listOf(clock.now.toString()), query("SELECT last_seen_at FROM players WHERE id = 123") { it.getString(0) })
        assertEquals(HttpStatusCode.Found, browser.get(Access()).status)
    }

    @Test
    fun `without a session no page reveals the alliance`() = appTest {
        syncMembers()
        signIn("Fourmi")
        signOut()

        for (page in listOf("/", "/wages", "/update-levels", "/my-deliveries", "/debts", "/access/links")) {
            val res = browser.get(page)
            assertEquals(HttpStatusCode.Found, res.status, page)
            assertEquals("/access", res.headers[HttpHeaders.Location], page)
        }
        assertEquals(HttpStatusCode.Found, browser.submitForm("/debts", parameters { append("player-124", "ACCUMULATE_DEBT") }).status)
        assertEquals(listOf("DELIVER"), query("SELECT wage_settlement FROM players WHERE id = 124") { it.getString(0) })

        val access = client.get(Access()).bodyAsText()
        assertTrue("<h1 class=\"page-title\">Accès membre</h1>" in access)
        assertFalse("emp-nav" in access)
        assertTrue("Écris à un chef dans Fourmizzz : <strong>Fourmi</strong>." in access)
        assertFalse("abeille" in access)
    }

    @Test
    fun `an unknown link opens nothing`() = appTest {
        syncMembers()

        val res = open("/access/0123456789abcdef")

        assertEquals(HttpStatusCode.Forbidden, res.status)
        assertTrue("Ce lien n'ouvre pas d'accès" in res.bodyAsText())
        assertTrue(res.setCookie().isEmpty())
    }

    @Test
    fun `a chief copies the access message of a member which signs them in`() = appTest {
        syncMembers()
        signIn("Fourmi")

        val before = client.get(Access.Links()).bodyAsText()
        val message = copyMessage(abeille.id)
        val res = open(link(message))
        val home = client.get(Home()).bodyAsText()
        signIn("Fourmi")
        val after = client.get(Access.Links()).bodyAsText()

        assertTrue("""<h1 class="page-title">Accès des membres</h1><span class="emp-badge emp-badge--leader">Chef</span>""" in before.replace(Regex(">\\s+<"), "><"))
        assertTrue("""<span class="text-secondary">Jamais connecté</span>""" in before)
        assertTrue("""<form action="/access/links/124/message" method="post"><button type="submit" class="emp-btn emp-btn--secondary emp-btn--small" data-copy="">Copier le MP d'accès</button>""" in before.replace(Regex(">\\s+<"), "><"))
        assertTrue(message.startsWith("Voici ton lien personnel pour le site de l'alliance :\nhttp://localhost/access/"))
        assertTrue("« Forum externe »" in message)
        assertEquals(HttpStatusCode.Found, res.status)
        assertFalse("Accès des membres" in home)
        assertTrue("Lien envoyé le 20${NBSP}oct. · dernière visite à l'instant" in after)
        assertTrue("Remplacer le lien de abeille" in after)
    }

    @Test
    fun `copying a message again renews the link and keeps the devices signed in`() = appTest {
        syncMembers()
        signIn("Fourmi")
        val first = link(copyMessage(abeille.id))
        val second = link(copyMessage(abeille.id))

        assertEquals(HttpStatusCode.Forbidden, open(first).status)
        assertEquals(HttpStatusCode.Found, open(second).status)
        val member = identity.session
        signIn("Fourmi")
        copyMessage(abeille.id)

        assertEquals(HttpStatusCode.Forbidden, open(second).status)
        identity.session = member
        assertEquals(HttpStatusCode.OK, browser.get(Home()).status)
    }

    @Test
    fun `without the script the message is shown to be copied by hand`() = appTest {
        syncMembers()
        signIn("Fourmi")

        val page = client.post(Access.Links.Message(playerId = abeille.id)).bodyAsText()

        assertTrue("Message pour abeille, objet « Ton accès EMP »" in page)
        assertTrue(Regex("""<textarea[^>]*id="access-message"[^>]*>Voici ton lien personnel""").containsMatchIn(page))
    }

    @Test
    fun `replacing a link signs out every device it opened`() = appTest {
        syncMembers()
        signIn("Fourmi")
        val old = link(copyMessage(abeille.id))
        open(old)
        val phone = identity.session
        val code = showDeviceCode()
        val computer = typeCode(code).setCookie().single { it.name == SESSION_COOKIE }.value
        identity.session = computer
        assertEquals(HttpStatusCode.OK, browser.get(Home()).status)

        signIn("Fourmi")
        val res = client.submitForm("/access/links/124/replace", parameters { })

        assertEquals("/access/links?replaced=124", res.headers[HttpHeaders.Location])
        assertTrue("Lien de abeille remplacé" in client.get(res.headers[HttpHeaders.Location]!!).bodyAsText())
        for (session in listOf(phone, computer)) {
            identity.session = session
            assertEquals("/access", browser.get(Home()).headers[HttpHeaders.Location])
        }
        assertEquals(HttpStatusCode.Forbidden, open(old).status)
        signIn("Fourmi")
        assertEquals(HttpStatusCode.Found, open(link(copyMessage(abeille.id))).status)
    }

    @Test
    fun `a player who left the alliance loses access`() = appTest {
        syncMembers()
        signIn("abeille", chief = false)
        fourmizzz.players("202610201300", listOf(fourmi))
        sync(Exports.Players.Sync(version = "202610201300"))

        val res = browser.get(Home())

        assertEquals("/access?notice=LEFT", res.headers[HttpHeaders.Location])
        assertTrue("Tu ne fais plus partie de l'alliance EMP" in client.get(res.headers[HttpHeaders.Location]!!).bodyAsText())
    }

    @Test
    fun `only chiefs give access`() = appTest {
        syncMembers()
        signIn("abeille", chief = false)

        assertEquals(HttpStatusCode.Forbidden, client.get(Access.Links()).status)
        assertEquals(HttpStatusCode.Forbidden, client.post(Access.Links.Message(playerId = abeille.id)).status)
        assertEquals(HttpStatusCode.Forbidden, client.submitForm("/access/links/123/replace", parameters { }).status)
        assertTrue(query("SELECT revoked_at FROM access_links WHERE player_id = 123 AND revoked_at IS NOT NULL") { it.getString(0) }.isEmpty())
    }

    @Test
    fun `the API takes its token or a chief's session`() = appTest {
        fourmizzz.players("202610201200", listOf(fourmi, abeille))
        identity.apiToken = null

        assertEquals(HttpStatusCode.Unauthorized, sync(Exports.Players.Sync()).status)
        identity.apiToken = "wrong"
        assertEquals(HttpStatusCode.Unauthorized, sync(Exports.Players.Sync()).status)
        identity.apiToken = API_TOKEN
        assertEquals(HttpStatusCode.OK, sync(Exports.Players.Sync()).status)

        identity.apiToken = null
        signIn("abeille", chief = false)
        assertEquals(HttpStatusCode.Forbidden, sync(Exports.Players.Sync()).status)
        signIn("Fourmi")
        assertEquals(HttpStatusCode.OK, sync(Exports.Players.Sync()).status)
    }

    @Test
    fun `posts from another site are refused`() = appTest {
        syncMembers()
        signIn("Fourmi")

        val foreign = client.submitForm("/debts", parameters { append("player-124", "ACCUMULATE_DEBT") }) {
            header(HttpHeaders.Origin, "https://evil.example")
        }
        val same = client.submitForm("/debts", parameters { append("player-124", "ACCUMULATE_DEBT") }) {
            header(HttpHeaders.Origin, "http://localhost")
        }

        assertEquals(HttpStatusCode.Forbidden, foreign.status)
        assertEquals(HttpStatusCode.Found, same.status)
    }

    @Test
    fun `a device code signs another browser in once`() = appTest {
        syncMembers()
        signIn("abeille", chief = false)

        val code = showDeviceCode()
        val page = client.post(Access.Device()).bodyAsText()
        signOut()
        val typed = code.lowercase().replace("-", " ")
        val res = typeCode(typed)
        val again = typeCode(code)

        assertTrue(Regex("""^[2-9A-HJKMNP-Z]{3}-[2-9A-HJKMNP-Z]{3}$""").matches(code))
        assertTrue("Valable 10 minutes, une seule fois." in page)
        assertEquals(HttpStatusCode.Found, res.status)
        assertEquals("/", res.headers[HttpHeaders.Location])
        identity.session = res.setCookie().single { it.name == SESSION_COOKIE }.value
        assertTrue("Mes livraisons" in client.get("/my-deliveries").bodyAsText())
        assertEquals(HttpStatusCode.BadRequest, again.status)
        assertTrue("Code expiré ou déjà utilisé." in again.bodyAsText())
    }

    @Test
    fun `a device code expires after ten minutes`() = appTest {
        syncMembers()
        signIn("abeille", chief = false)
        val code = showDeviceCode()
        signOut()

        clock.now += 10.minutes
        val res = typeCode(code)

        assertEquals(HttpStatusCode.BadRequest, res.status)
        assertTrue("Code expiré ou déjà utilisé." in res.bodyAsText())
        assertTrue(res.setCookie().isEmpty())
    }

    @Test
    fun `typing codes is limited`() = appTest {
        syncMembers()
        signIn("abeille", chief = false)
        val code = showDeviceCode()
        signOut()

        repeat(10) {
            val res = typeCode("AAAAAA")
            assertEquals(HttpStatusCode.BadRequest, res.status)
            assertTrue("Code inconnu" in res.bodyAsText())
        }
        val blocked = typeCode(code)
        clock.now += 10.minutes + 1.minutes
        val later = typeCode("AAAAAA")

        assertEquals(HttpStatusCode.TooManyRequests, blocked.status)
        assertTrue("Trop d'essais." in blocked.bodyAsText())
        assertEquals(HttpStatusCode.BadRequest, later.status)
    }
}
