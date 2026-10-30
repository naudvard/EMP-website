# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

EMP is a website for the EMP alliance of the browser game Fourmizzz (s5): it syncs the game's public exports, computes the daily wages (wood) members send each other, and tracks convoys and debts. UI text and docs are in French; code identifiers, CSS classes and URLs are in English, like the design system they follow (`emp-card`, `empCard`, `BtnVariant.PRIMARY`, `/wages`).

## Commands

Kotlin/Native only, with a single target picked from the host (`mingwX64` on Windows, `macosArm64`, `linuxX64`), or forced with `-PnativeTarget=linuxArm64` (or `linuxX64`) to cross-compile: the compiler doesn't run on Linux arm64, so CI builds that binary on x64, where its tests can't run. Task names below use `MingwX64`; substitute the host target.

```bash
./gradlew linkDebugExecutableMingwX64        # build → build/bin/mingwX64/debugExecutable/emp.exe
./gradlew mingwX64Test                       # all tests (or ./gradlew allTests)
./gradlew mingwX64Test --tests "fr.nathanaudvard.emp.WagesPageTest"
./gradlew mingwX64Test --tests "fr.nathanaudvard.emp.WagesPageTest.says when no wages have been computed yet"
```

The first build downloads the SQLite amalgamation from sqlite.org (checksum-verified) and compiles it into the binary. There is no linter configured beyond `kotlin.code.style=official`.

Run the binary directly; config comes from `application.yaml` with env overrides: `PORT` (3000), `HOST`, `DATABASE_PATH` (`./data/app.sqlite`), `FOURMIZZZ_BASE_URL`, `FOURMIZZZ_TIMEOUT_MS`, `ALLIANCE_TAG` (EMP), `API_TOKEN` (bearer token for external API calls; empty = only a chief's session), `PUBLIC_URL` (base of the access links; empty = the request's). `emp chief-link <pseudo>` makes a member of the latest synced export a chief and prints their access link: the bootstrap for the first chief. `.claude/launch.json` has preview configs (`emp-windows`) that point `DATABASE_PATH` at a scratch file — don't run against `data/app.sqlite`, it holds real data.

## Build traps

- **Native compiler cache is disabled** for Kotlin 2.4.21 in `build.gradle.kts` because SQLite is compiled via `-Xcompile-source` and gets stripped otherwise. When bumping Kotlin, the `DisableCacheInKotlinVersion` entry must be renewed.
- **No resources on Kotlin/Native**: every `.css` and `.js` under `src/nativeMain/assets/` is turned by the `generateAssets` task into a Kotlin constant `<name>Css` / `<name>Js` (`build/generated/assets/.../Assets.kt`) whose served name embeds a content hash. A new stylesheet or script must also be added to the `stylesheets` or `scripts` list in `web/assets/Asset.kt` (load order matters: tokens, bundle, base; htmx, emp). The file name becomes a Kotlin identifier: no dots nor dashes (`htmx.js`, not `htmx-2.0.11.js`). `AssetsController` serves them with an immutable cache.
- **Two scripts, loaded `defer` by every page**: the site requires JavaScript, with no fallback. Without it, `<html class="js">` is never set and the page only shows its `<noscript>` message (`Layout.kt`, `base.css`). `assets/htmx.js` is [htmx](https://htmx.org) copied as is (version and checksum in `assets/README.md`, configured by the `htmx-config` meta of `Layout.kt`). A form calling `live("id1", "id2")` (`Components.kt`) is posted by htmx, which swaps the elements of those ids from the answer: its route sits in an `hx { }` block of [Ktor HTMX](https://ktor.io/docs/htmx-integration.html) (no route for a post without `HX-Request`, so tests send `htmx()`), always answers with `respondSections { … }` (`web/Htmx.kt`), and `call.redirect(url)` sends htmx to a whole page (`HX-Redirect`). Ktor HTMX is server-side only: `htmx.js` is still served. A card whose presence changes is swapped with its column (`MeSection.SENDING`), never alone. Pages are worked out once in a page object (`MyPage`, `MemberPage`) shared by the page and its sections. `assets/emp.js` reloads the page when htmx fails to post and handles the clipboard (`data-copy`, `data-paste`; the text to copy is shown on the page when the clipboard can't be written), paste-to-submit fields, forms saved as soon as a choice changes (`data-submit-on-change`, with `data-confirm` asking first) and the Salaires and Alliance pseudo filters.
- **No reflection**: Ktor modules can't be loaded from `application.yaml`; `main()` wires `Application.module()` explicitly, and every dependency is registered by hand in `Dependencies.kt`.
- **No `by dependencies`**: off the JVM the delegate only returns a dependency already created, else throws `MissingDependencyException`. Configuration code uses the suspending `dependencies.resolve()`.

## Architecture

Ktor server (CIO) + kotlinx.html server-rendered pages + SQLDelight over SQLite (WAL, foreign keys on).

Request flow: `Routing.kt` resolves services from Ktor DI and passes them as parameters to `Route.xxxController(...)` extension functions. Adding a feature means: SQL in `.sq` → repository (`database/`) → service method (`domain/service/`, one per action, grouped by theme: Access, Alliance, Player, Convoy, Wage, Export) → register in `Dependencies.kt` → resolve in `Routing.kt` → controller. A service calls the repositories directly; `domain/helper/` is not a layer to go through, only the place for business logic that is complex or shared by several services (token hashing, defaults of a member never saved, players export sync, wage maths).

- `api/`: JSON API under `/api/v1` (sync of Fourmizzz exports, wage computation, health). Routes are typed Ktor `@Resource` classes in `api/resources/`; Bruno/OpenCollection request samples live in `docs/API/`.
- `web/`: HTML pages. `resources/` = typed routes, `controllers/` = handlers and form parsing, `pages/` = kotlinx.html rendering (`Layout.kt` shell + nav, `Components.kt` design-system helpers emitting `emp-*` classes, `Formats.kt` number/date formatting in game time zone). Never hardcode colours/sizes inline — use the `emp-*` components and tokens.
- `fourmizzz/`: HTTP client + DTOs for the game's export API (`/api/exports/{type}/?version=`).
- `domain/helper/WageCalculation.kt`: the pure wage maths (field production, colony → master share rounded once, laying needs, debt settlement). Heavily tested in `WageCalculationTest`.
- `config/`: `AppConfig` (deserialised from the `app` section), serialization, monitoring, StatusPages (maps domain exceptions to JSON errors — note it would also replace HTML 404 bodies, so web pages render "not found" themselves with 200).
- Daily computation: an external cron calls `POST /api/v1/alliances/{tag}/wages` (without `distribution`, the one chiefs save in `alliance_settings`). The app runs no scheduler; `computation_time` (18:00 game time) only drives the "prochain calcul" shown on the pages and must match the cron. Its field is commented out of the Salaires panel until it can change the cron too.
- `Clock` is injected everywhere time matters; never call `Clock.System` directly.
- Identity (`config/Identity.kt`, Ktor Sessions + Authentication): a member opens their personal link `/access/{token}`, which works once (other devices sign in with a code shown on that one) and sets a one-year `HttpOnly; Secure; SameSite=Lax` session cookie renewed at each visit. Tokens and session ids are only stored hashed. Pages live under `memberAccess { }` (no valid session → redirect to `/access`, player left the alliance → `/access?notice=LEFT`); controllers read `call.viewer` and enforce rights with `call.requireChief()` / `call.requireEdit(playerId)` (403). The API lives under `apiAccess { }`: `Authorization: Bearer $API_TOKEN` or a chief's session. Security is sized for an alliance tool: `SameSite=Lax` is the only CSRF protection, and device codes (6 characters, 10 minutes, single use) have no attempt limit.
- HTTP (`config/Http.kt`): the site is only reached through the reverse proxy; `XForwardedHeaders` gives the links sent the address it was reached at. Security headers come from `DefaultHeaders`, bodies are capped by `RequestBodyLimit` (413).

### Database

SQLDelight with `deriveSchemaFromMigrations = true`: the schema is the sum of the numbered migrations `src/commonMain/sqldelight/.../db/N.sqm`. Schema changes go in a new `N+1.sqm`; never edit an existing migration. Queries live in the `.sq` files next to them.

### Tests

`src/nativeTest`: mostly end-to-end through `appTest { ... }` (`TestHelpers.kt`), which boots the full module on a temporary SQLite file, fakes the Fourmizzz export API (`FakeFourmizzz`: `fourmizzz.players(version, …)`, `failing`, `rejectedVersions`) and injects a `TestClock` fixed at `NOW`. The test client sends the API token and, after `signIn(pseudo, chief = true)` (the member must be synced), the session cookie. Helpers: `signIn`, `syncAndSignIn()`, `signOut()`, `sync(resource)`, `post(resource)`, `execute(sql)`, `query(sql)`, `members()`, `latestComputation()`. Page tests assert on the rendered HTML, flattened, with string checks, and read structured parts (rows, cards, tiles) with Ksoup CSS selectors rather than regexes; Power-assert prints the value of each part of a failed `kotlin.test` assertion. Test names are backtick sentences.
