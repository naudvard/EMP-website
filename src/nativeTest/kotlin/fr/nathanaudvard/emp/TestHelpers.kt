package fr.nathanaudvard.emp

import app.cash.sqldelight.db.QueryResult
import com.fleeksoft.ksoup.Ksoup
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import fr.nathanaudvard.emp.api.dto.SyncRequest
import fr.nathanaudvard.emp.api.resources.Exports
import fr.nathanaudvard.emp.database.PlayerRepository
import fr.nathanaudvard.emp.domain.helper.AccessHelper
import fr.nathanaudvard.emp.domain.model.AllianceMember
import fr.nathanaudvard.emp.domain.model.AllianceSettings
import fr.nathanaudvard.emp.domain.model.ExportType
import fr.nathanaudvard.emp.domain.model.WageComputation
import fr.nathanaudvard.emp.domain.model.WageHistory
import fr.nathanaudvard.emp.domain.service.AllianceService
import fr.nathanaudvard.emp.domain.service.WageService
import fr.nathanaudvard.emp.fourmizzz.AllianceRes
import fr.nathanaudvard.emp.fourmizzz.DiplomacyRes
import fr.nathanaudvard.emp.fourmizzz.ExportVersionsRes
import fr.nathanaudvard.emp.fourmizzz.PactRes
import fr.nathanaudvard.emp.fourmizzz.PlayerRes
import fr.nathanaudvard.emp.config.SESSION_COOKIE
import fr.nathanaudvard.emp.web.resources.Access
import io.ktor.client.HttpClient
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.client.plugins.resources.Resources
import io.ktor.client.plugins.resources.get
import io.ktor.client.plugins.resources.post
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.setCookie
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.plugins.di.resolve
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement

val empire = AllianceRes(
    tag = "EMP",
    name = "Empire",
    playersCount = 12,
    totalField = 1_000,
    totalBuildingScore = 100,
    totalTechnologyScore = 50,
    totalTrophyScore = 0,
    diplomacy = DiplomacyRes(pacts = listOf(PactRes(tag = "ODF", name = "PNA", description = "")), wars = listOf("LRM")),
)

val fourmi = PlayerRes(
    id = 123,
    pseudo = "Fourmi",
    alliance = "EMP",
    masterPlayerId = null,
    x = 10,
    y = -4,
    field = 98_765,
    grade = "Chef",
    buildingScore = 1_500,
    technologyScore = 700,
    trophyScore = 1_234,
    onHoliday = false,
    isBanned = false,
)

class FakeFourmizzz {
    private val exports = ExportType.entries.associateWith { mutableMapOf<String, JsonElement>() }

    val rejectedVersions = mutableSetOf<String>()

    var failing = false

    // The next export requests to fail, as a passing hiccup
    var failuresLeft = 0

    var exportRequests = 0

    operator fun set(type: ExportType, version: String, body: JsonElement) {
        exports.getValue(type)[version] = body
    }

    fun alliances(version: String, alliances: List<AllianceRes>) =
        set(ExportType.ALLIANCES, version, Json.encodeToJsonElement(alliances))

    fun players(version: String, players: List<PlayerRes>) =
        set(ExportType.PLAYERS, version, Json.encodeToJsonElement(players))

    private fun versions(type: ExportType) = exports.getValue(type).keys.sortedDescending()

    fun Application.serve() {
        install(ContentNegotiation) { json() }
        routing {
            get("/api/exports/") {
                call.respond(ExportVersionsRes(players = versions(ExportType.PLAYERS), alliances = versions(ExportType.ALLIANCES)))
            }
            get("/api/exports/{type}/") {
                exportRequests++
                val type = ExportType.entries.single { it.name.lowercase() == call.parameters["type"] }
                val requested = call.request.queryParameters["version"] ?: "latest"
                val body = (if (requested == "latest") versions(type).firstOrNull() else requested)
                    ?.let { exports.getValue(type)[it] }
                when {
                    failing || failuresLeft-- > 0 ->
                        call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Erreur interne"))
                    requested in rejectedVersions -> call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Parametre version invalide"))
                    body == null -> call.respond(HttpStatusCode.NotFound, mapOf("error" to "Aucun export pour cette version."))
                    else -> call.respond(body)
                }
            }
        }
    }
}

class TestClock(var now: Instant = NOW) : Clock {
    override fun now() = now
}

// What the test client sends with each request: the API token, and the session cookie once signed in
class TestIdentity {
    var apiToken: String? = API_TOKEN
    var session: String? = null

    val plugin = createClientPlugin("TestIdentity") {
        onRequest { request, _ ->
            apiToken?.let { request.headers.append(HttpHeaders.Authorization, "Bearer $it") }
            session?.let { request.headers.append(HttpHeaders.Cookie, "$SESSION_COOKIE=$it") }
        }
    }
}

class AppTestScope(
    private val builder: ApplicationTestBuilder,
    val fourmizzz: FakeFourmizzz,
    val clock: TestClock,
    val identity: TestIdentity,
) {
    val client: HttpClient get() = builder.client

    val application get() = builder.application

    // Opens the member's link as a browser would, the member being a chief or not. Their alliance must be synced
    suspend fun signIn(pseudo: String, chief: Boolean = true) {
        val member = members().single { it.pseudo == pseudo }
        val access: AccessHelper = builder.application.dependencies.resolve()
        val players: PlayerRepository = builder.application.dependencies.resolve()
        players.setChief(member.id, chief)
        val token = access.issueLink(member.id, createdBy = null, clock.now)
        val res = client.config { followRedirects = false }.get(Access.Link(token = token))
        check(res.status == HttpStatusCode.Found) { "The link of $pseudo opened no session: ${res.status}" }
        identity.session = res.setCookie().single { it.name == SESSION_COOKIE }.value
    }

    // A single member, signed in as a chief, for the pages that need nothing else
    suspend fun syncAndSignIn() {
        fourmizzz.players("202610201200", listOf(fourmi))
        sync(Exports.Players.Sync(version = "202610201200"))
        signIn(fourmi.pseudo)
    }

    fun signOut() {
        identity.session = null
    }

    suspend inline fun <reified T : Any> post(resource: T): HttpResponse = client.post(resource)

    suspend inline fun <reified T : Any> sync(resource: T, tags: List<String> = listOf("EMP")): HttpResponse =
        client.post(resource) {
            contentType(ContentType.Application.Json)
            setBody(SyncRequest(tags))
        }

    suspend fun execute(sql: String) {
        val driver: SqlDriver = builder.application.dependencies.resolve()
        driver.execute(null, sql, 0)
    }

    suspend fun members(allianceTag: String = "EMP"): List<AllianceMember> {
        val alliance: AllianceService = builder.application.dependencies.resolve()
        return alliance.members(allianceTag)
    }

    suspend fun latestComputation(allianceTag: String = "EMP"): WageComputation {
        val wages: WageService = builder.application.dependencies.resolve()
        return checkNotNull(wages.history(allianceTag).selected)
    }

    suspend fun wageHistory(allianceTag: String = "EMP"): WageHistory {
        val wages: WageService = builder.application.dependencies.resolve()
        return wages.history(allianceTag)
    }

    // The application starts with the first request: nothing can be resolved before
    suspend fun start() = builder.startApplication()

    suspend fun settings(allianceTag: String = "EMP"): AllianceSettings {
        val wages: WageService = builder.application.dependencies.resolve()
        return wages.settings(allianceTag)
    }

    suspend fun <T> query(sql: String, mapper: (SqlCursor) -> T): List<T> {
        val driver: SqlDriver = builder.application.dependencies.resolve()
        return driver.executeQuery(null, sql, { cursor ->
            QueryResult.Value(buildList { while (cursor.next().value) add(mapper(cursor)) })
        }, 0).value
    }
}

private const val FOURMIZZZ_URL = "http://fourmizzz.test"

const val API_TOKEN = "test-api-token"

val NOW: Instant = Instant.parse("2026-10-20T12:30:00Z")

fun appTest(fourmizzzUrl: String = FOURMIZZZ_URL, block: suspend AppTestScope.() -> Unit) {
    val databasePath = Path(SystemTemporaryDirectory, "site-test-${Random.nextLong().toULong()}.sqlite")
    try {
        testApplication {
            val fourmizzz = FakeFourmizzz()
            val clock = TestClock()
            val identity = TestIdentity()

            environment {
                config = MapApplicationConfig(
                    "app.database.path" to databasePath.toString(),
                    "app.fourmizzz.baseUrl" to fourmizzzUrl,
                    "app.fourmizzz.timeoutMs" to "1000",
                    "app.fourmizzz.retryDelayMs" to "0",
                    "app.alliance.tag" to "EMP",
                    "app.api.token" to API_TOKEN,
                )
            }
            externalServices {
                hosts(FOURMIZZZ_URL) {
                    with(fourmizzz) { serve() }
                }
            }
            application {
                dependencies.provide<HttpClient> { this@testApplication.createClient { } }
                dependencies.provide<Clock> { clock }
                module()
            }
            client = createClient {
                install(Resources)
                install(ClientContentNegotiation) { json() }
                install(identity.plugin)
            }

            AppTestScope(this, fourmizzz, clock, identity).block()
        }
    } finally {
        // A failed test leaves the database open: deleting it must not hide the failure
        for (suffix in listOf("", "-wal", "-shm")) {
            runCatching { SystemFileSystem.delete(Path("$databasePath$suffix"), mustExist = false) }
        }
    }
}

// As htmx posts a form: the answer holds the sections it swaps
fun HttpRequestBuilder.htmx() = header("HX-Request", "true")

// The ids of the sections an answer to htmx holds, in order
fun sectionIds(answer: String) = Ksoup.parse(answer).body().child(0).children().map { it.id() }
