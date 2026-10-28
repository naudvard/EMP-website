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

Run the binary directly; config comes from `application.yaml` with env overrides: `PORT` (3000), `HOST`, `DATABASE_PATH` (`./data/app.sqlite`), `FOURMIZZZ_BASE_URL`, `FOURMIZZZ_TIMEOUT_MS`, `ALLIANCE_TAG` (EMP). `.claude/launch.json` has preview configs (`emp-windows`) that point `DATABASE_PATH` at a scratch file — don't run against `data/app.sqlite`, it holds real data.

## Build traps

- **Native compiler cache is disabled** for Kotlin 2.4.21 in `build.gradle.kts` because SQLite is compiled via `-Xcompile-source` and gets stripped otherwise. When bumping Kotlin, the `DisableCacheInKotlinVersion` entry must be renewed.
- **No resources on Kotlin/Native**: every `.css` under `src/nativeMain/assets/` is turned by the `generateStylesheets` task into a Kotlin constant `<name>Css` (`build/generated/stylesheets/.../Stylesheets.kt`) whose served name embeds a content hash. A new stylesheet must also be added to the `stylesheets` list in `web/assets/Stylesheet.kt` (load order matters: tokens, bundle, base). `AssetsController` serves them with an immutable cache.
- **No reflection**: Ktor modules can't be loaded from `application.yaml`; `main()` wires `Application.module()` explicitly, and every dependency is registered by hand in `Dependencies.kt`.

## Architecture

Ktor server (CIO) + kotlinx.html server-rendered pages + SQLDelight over SQLite (WAL, foreign keys on).

Request flow: `Routing.kt` resolves use cases from Ktor DI and passes them as parameters to `Route.xxxController(...)` extension functions. Adding a feature means: SQL in `.sq` → repository (`database/`) → service (`service/`) → use case (`domain/usecase/`, an `operator fun invoke`) → register in `Dependencies.kt` → resolve in `Routing.kt` → controller.

- `api/`: JSON API under `/api/v1` (sync of Fourmizzz exports, wage computation, health). Routes are typed Ktor `@Resource` classes in `api/resources/`; Bruno/OpenCollection request samples live in `docs/API/`.
- `web/`: HTML pages. `resources/` = typed routes, `controllers/` = handlers, `forms/` = form parsing, `pages/` = kotlinx.html rendering (`Layout.kt` shell + nav, `Components.kt` design-system helpers emitting `emp-*` classes, `Formats.kt` number/date formatting in game time zone). Never hardcode colours/sizes inline — use the `emp-*` components and tokens.
- `fourmizzz/`: HTTP client + DTOs for the game's export API (`/api/exports/{type}/?version=`).
- `service/WageCalculation.kt`: the pure wage maths (field production, colony → master share rounded once, laying needs, debt settlement). Heavily tested in `WageCalculationTest`.
- `config/`: `AppConfig` (deserialised from the `app` section), serialization, monitoring, StatusPages (maps domain exceptions to JSON errors — note it would also replace HTML 404 bodies, so web pages render "not found" themselves with 200).
- `Clock` is injected everywhere time matters; never call `Clock.System` directly.

### Database

SQLDelight with `deriveSchemaFromMigrations = true`: the schema is the sum of the numbered migrations `src/commonMain/sqldelight/.../db/N.sqm`. Schema changes go in a new `N+1.sqm`; never edit an existing migration. Queries live in the `.sq` files next to them.

### Tests

`src/nativeTest`: mostly end-to-end through `appTest { ... }` (`TestHelpers.kt`), which boots the full module on a temporary SQLite file, fakes the Fourmizzz export API (`FakeFourmizzz`: `fourmizzz.players(version, …)`, `failing`, `rejectedVersions`) and injects a `TestClock` fixed at `NOW`. Helpers: `sync(resource)`, `post(resource)`, `execute(sql)`, `query(sql)`, `members()`, `latestComputation()`. Page tests assert on the rendered HTML with regexes; test names are backtick sentences.
