# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

EMP is a website for the EMP alliance of the browser game Fourmizzz (s5): it syncs the game's public exports, computes the daily wages (wood) members send each other, and tracks convoys and debts. UI text and docs are in French; code identifiers, CSS classes and URLs are in English, like the design system they follow (`emp-card`, `empCard`, `BtnVariant.PRIMARY`, `/wages`).

## Commands

Kotlin/Native only, with a single target picked from the host (`mingwX64` on Windows, `macosArm64`, `linuxX64`, `linuxArm64`). Task names below use `MingwX64`; substitute the host target.

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
- **No resources on Kotlin/Native**: every `.css` and `.js` under `src/nativeMain/assets/` is turned by the `generateAssets` task into a Kotlin constant `<name>Css` / `<name>Js` (`build/generated/assets/.../Assets.kt`) whose served name embeds a content hash. A new stylesheet must also be added to the `stylesheets` list in `web/assets/Asset.kt` (load order matters: tokens, bundle, base). `AssetsController` serves them with an immutable cache.
- **One shared script** (`assets/emp.js`, loaded `defer` by every page): every page must work without it. Forms marked `data-emp-live="id1 id2"` are posted with `fetch`; the server answers as without JavaScript (redirect to the page, or the page itself) and the script swaps those ids (the whole `main` when one is missing on one side). It also handles the clipboard (`data-copy`, `data-paste`), paste-to-submit fields, forms saved as soon as a choice changes (`data-submit-on-change`, with `data-confirm` asking first) and the Salaires and Alliance pseudo filters. `<html class="js">` is set early for styles that need the script.
- **No reflection**: Ktor modules can't be loaded from `application.yaml`; `main()` wires `Application.module()` explicitly, and every dependency is registered by hand in `Dependencies.kt`.

## Architecture

Ktor server (CIO) + kotlinx.html server-rendered pages + SQLDelight over SQLite (WAL, foreign keys on).

Request flow: `Routing.kt` resolves use cases from Ktor DI and passes them as parameters to `Route.xxxController(...)` extension functions. Adding a feature means: SQL in `.sq` → repository (`database/`) → service (`service/`) → use case (`domain/usecase/`, an `operator fun invoke`) → register in `Dependencies.kt` → resolve in `Routing.kt` → controller.

- `api/`: JSON API under `/api/v1` (sync of Fourmizzz exports, wage computation, health). Routes are typed Ktor `@Resource` classes in `api/resources/`; Bruno/OpenCollection request samples live in `docs/API/`.
- `web/`: HTML pages. `resources/` = typed routes, `controllers/` = handlers and form parsing, `pages/` = kotlinx.html rendering (`Layout.kt` shell + nav, `Components.kt` design-system helpers emitting `emp-*` classes, `Formats.kt` number/date formatting in game time zone). Never hardcode colours/sizes inline — use the `emp-*` components and tokens.
- `fourmizzz/`: HTTP client + DTOs for the game's export API (`/api/exports/{type}/?version=`).
- `service/WageCalculation.kt`: the pure wage maths (field production, colony → master share rounded once, laying needs, debt settlement). Heavily tested in `WageCalculationTest`.
- `config/`: `AppConfig` (deserialised from the `app` section), serialization, monitoring, StatusPages (maps domain exceptions to JSON errors — note it would also replace HTML 404 bodies, so web pages render "not found" themselves with 200).
- `WageSchedule.kt`: a coroutine started with the application computes the wages each day at the time chiefs save in `alliance_settings` (18:00 game time and minimal distribution until then), skipping when a computation was made within the minute; a failure is kept for the Salaires panel. It first reads the database a minute after start, so tests never reach it: they call `RunScheduledComputationUseCase` directly.
- `Clock` is injected everywhere time matters; never call `Clock.System` directly.
- Identity (`config/Identity.kt`): a member opens their personal link `/access/{token}` once per device, which sets a one-year `HttpOnly; Secure; SameSite=Lax` session cookie renewed at each visit. Tokens and session ids are only stored hashed. Pages live under `memberAccess { }` (no valid session → redirect to `/access`, player left the alliance → `/access?notice=LEFT`); controllers read `call.viewer` and enforce rights with `call.requireChief()` / `call.requireEdit(playerId)` (403). The API lives under `apiAccess { }`: `Authorization: Bearer $API_TOKEN` or a chief's session. Posts whose `Origin` is another host are refused.

### Database

SQLDelight with `deriveSchemaFromMigrations = true`: the schema is the sum of the numbered migrations `src/commonMain/sqldelight/.../db/N.sqm`. Schema changes go in a new `N+1.sqm`; never edit an existing migration. Queries live in the `.sq` files next to them.

### Tests

`src/nativeTest`: mostly end-to-end through `appTest { ... }` (`TestHelpers.kt`), which boots the full module on a temporary SQLite file, fakes the Fourmizzz export API (`FakeFourmizzz`: `fourmizzz.players(version, …)`, `failing`, `rejectedVersions`) and injects a `TestClock` fixed at `NOW`. The test client sends the API token and, after `signIn(pseudo, chief = true)` (the member must be synced), the session cookie. Helpers: `signIn`, `syncAndSignIn()`, `signOut()`, `sync(resource)`, `post(resource)`, `execute(sql)`, `query(sql)`, `members()`, `latestComputation()`. Page tests assert on the rendered HTML with regexes; test names are backtick sentences.
