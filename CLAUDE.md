# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Kotlin/JVM (Java 17) multi-module Gradle app backing the FulguroGo Discord community. A single process runs many
long-lived background services that scrape/poll Go servers (KGS, OGS, FOX, IGS) and rating federations (FFG, EGF),
store users and games in MySQL, derive a custom "Gold" ladder rating/tier per player, notify Discord, and expose a
JSON API for the website frontend.

There are no tests in the repo (`src/test` does not exist anywhere), no linter config, and no CI. Verification means
compiling and running.

## Commands

```bash
./gradlew build                  # compile everything
./gradlew :modules:ogs:build     # compile a single module
./gradlew :app:run               # run the whole app locally (needs config.properties, see below)
./gradlew :app:shadowJar         # fat jar -> app/build/libs/app-<version>-all.jar
./release.sh                     # prod build: swaps in prod config, shadowJar, moves to export/, restores dev config
```

`fulgurogo.version.name` in `gradle.properties` is the single source of the version (`release.sh` greps it for the
jar name). Bump it there; human-readable release notes go in `doc/changelog.txt` (French).

## Local setup you must know about

`modules/common/src/main/resources/config.properties` is **gitignored** (`*config.properties`) and the resources
directory is empty in a fresh clone. Nothing runs without it. The expected layout, per `release.sh`, is three files
in that directory: `dev.config.properties`, `prod.config.properties`, and `config.properties` (a copy of one of them
— dev in the working tree).

`Config.get(key)` reads that file with no defaults and no error handling, so a missing key surfaces as an NPE deep in
a service. Keys currently required include `debug`, `db.*`, `ssh.*`, `bot.*`, `user.agent`,
`global.read.timeout.ms`, `gold.api.port`, `gold.discord.auth.*`, and per-platform blocks (`kgs.archives.url`,
`ogs.*`, `fox.*`, `igs.*`, `ffg.website.url`, `egf.website.url`, `frontend.url`). The actual values live in
`assets/` on the maintainer's machine (see below).

When `debug=true`, `App.main` opens an SSH tunnel (`SSHConnector`, jsch, key at `ssh.private.key.file`) because the
prod MySQL only accepts local connections; `DatabaseAccessor` then connects to `ssh.forwarded.port` instead of
`db.port`. **Dev runs against the production database** — be careful with anything that writes or deletes.

## `assets/` — local-only scratch folder

Gitignored (`.gitignore:39`), untracked, and referenced by no code or build script. It won't exist in a fresh clone,
so never make code or a Gradle task depend on it. What's in it:

- `DevConfig.kt`, `ProdConfig.kt` — **real credentials in plaintext** (bot token, DB password, Discord OAuth secret,
  IGS and KGS logins). Read them if you need a config value, but never copy one into a tracked file, a commit, or a
  log line. `GitConfig.kt` is the same file with every secret replaced by an `<ENTER_...>` placeholder — that's the
  one to share or paste from.
- Those three files use the app's **previous** config format (`object Config { const val ... }`) rather than today's
  `config.properties` + `Config.get("key")`. Key names map predictably (`Bot.TOKEN` → `bot.token`), but some entries
  are for features no longer in the codebase: an `Exam` block (the "exam hunter", removed per `doc/changelog.txt`)
  and a KGS block pointing at a local proxy with account credentials.
- `kgs-proxy.war` — prebuilt webapp for that proxy, which fronted the real KGS API on `localhost:8080`. Dead weight
  as long as `KgsService` scrapes `gameArchives.jsp`; relevant again only if someone goes back to the KGS API.
- `v3 migration.sql` — an earlier, KGS-only draft of the v3 migration, and **not** the same as
  `doc/migration gold v3.sql`. It disagrees on `error` (`DATETIME` vs `TINYINT(1)`) and keys `kgs_games` on
  date + player names with `black_won`/`white_won` columns instead of a `gold_id` primary key. `doc/` is the
  authoritative version; don't apply this one.
- `database.txt` (manga titles) and `quizz-manga-raw.csv` (`category;question;answer`) — raw quiz data no current
  module reads. `icon.xd` is the Adobe XD source for the bot icon.

## Architecture

### Module pattern

Every feature is a Gradle module under `modules/` with an identical shape:

- `XModule` — an `object` with a 3-letter `const val TAG` (used by every log line from that module) and an `init()`
  that instantiates and `start()`s its services. `App.main` calls each `init()` in order: aggregators, then community
  modules (gold, fgc, api), then utilities (ping, clean).
- `XService : PeriodicFlowService` — the work loop.
- `db/XDatabaseAccessor` — an `object` owning that module's tables via `private const val`; all SQL lives here.
- `db/model/*` — sql2o-mapped data classes.

Dependency edges: `common` is the base (exposes hikari/jsch/jsoup/okhttp/sql2o/coroutines as `api`), `discord`
re-exports `common` plus JDA, platform modules depend on `discord` when they send notifications, and `api` depends on
every other module because it links accounts across all of them.

### The service loop

`PeriodicFlowService(initialDelayInSeconds, intervalInSeconds)` runs `onTick()` on a coroutine flow on
`Dispatchers.IO`. Any exception escaping the flow kills that service permanently (the handler logs and calls
`stop()`), which is why every `onTick()` body is wrapped in try/catch and guarded by a `processing` boolean re-entry
flag. Follow that pattern exactly when adding a service.

Aggregator services are **stalest-first single-item pollers**: each tick takes one row via
`stalestUser()` (`ORDER BY updated` with no `LIMIT` in some accessors), refreshes it, and stamps `updated = NOW()`.
Failures call `markAsError(...)`, which also stamps `updated` so a broken row rotates to the back of the queue
instead of blocking it. Tick intervals are deliberately staggered (discord 5s, ogs/gold/fgc 15s, kgs/fox/igs 60s,
ffg/egf 120s, ping/clean 600s) to spread outbound load.

### Data flow

1. Platform modules fill `<platform>_user_info` (one row per linked Discord user, holds the current rank string like
   `"12k"`/`"3d"`, or `"?"` when unknown) and, for KGS/OGS/FOX, `<platform>_games`.
2. `GoldService` reads the `gold_ranks` view through `GoldDatabaseAccessor.userRanks()`, and `UserRanks.computeRating()`
   converts each rank to a rating and averages it with hardcoded per-platform weights (KGS 0.8, OGS 1.0, FOX 0.1,
   IGS 0.6, FFG 0.7, EGF 0.7). The result is matched to a row of `gold_tiers` and written to `gold_ratings`;
   promotions are announced on Discord.
3. `FgcService` counts each player's recent valid games from the `fgc_validity_games` view into `fgc_validity`.
4. `ApiModule` starts Javalin on `gold.api.port` and serves `/gold/api/*` almost entirely out of two MySQL views,
   `api_players` and `api_games`.
5. `CleanService` deletes games older than 32 days and invalid accounts (the "phantom user" purge is commented out on
   purpose — it fired too aggressively).

Rank/rating math is centralized in `common/utilities/RankingUtilities.kt` (`ratingToRank`, `rankToRating`,
`rankToKyuDanString`, `kyuDanStringToRank`) — don't reimplement conversions locally.

### Database

sql2o over a HikariCP pool, one shared `DatabaseAccessor.dao`, always used as
`DatabaseAccessor.withDao { connection -> ... }` (opens and closes a connection per call; leak detection is at 5s).
Queries are hand-written strings with named parameters.

Two things bite when adding a column:

- **Column mapping is global.** `DatabaseAccessor` holds one `defaultColumnMappings` map translating every
  `snake_case` column to its camelCase property. A new snake_case column must be added there or it silently maps to
  null (most reads use `throwOnMappingFailure(false)`).
- **Models need a no-arg constructor.** Data classes are annotated `@GenerateNoArgConstructor`; the
  `kotlin-noarg` plugin is configured for that annotation in every module's `build.gradle.kts`.

There is no migration tool. `doc/migration gold v3.sql` is the reference schema (tables, plus the `gold_ranks`,
`fgc_validity_games`, `api_players`, `api_games` views) and it may have drifted from the live database. Schema and
view changes are applied by hand on the server.

### Cross-platform game identity

Games are keyed by `gold_id`, formatted `OGS_<id>` / `FOX_<id>` / `KGS_<black>_<white>_<epochMillis>` (KGS has no
game id, hence the composite). Games are inserted the first time they're seen — including while still
`result = "unfinished"`, so Discord can announce games in progress — then updated by `finishGame()` when a result
appears. Views filter `result != "unfinished"` so unfinished games never reach the API.

OGS has two paths that write the same table: `OgsService` polls the REST API for user profiles and finished games,
while `OgsRealTimeService` keeps a WebSocket open (auth via `ogs.auth.*`, ping every 10s, `gamelist/query` filtered
to known player ids, then `game/connect` per game). Both must respect the same insert-then-finish logic; a bug where
a correspondence game overwrote live results was fixed here recently, so be deliberate about which game a `gold_id`
refers to when touching either file.

### Outbound HTTP etiquette

Scraping is fragile and rate-sensitive. `common/utilities/HttpUtilities.kt` centralizes the okhttp client and the
jsoup `scrap(url)` helper, which sends a full browser-like header set and the configured `user.agent`. Services add
their own throttling (`ensureSpamDelay()` in `KgsService`, `FoxRetryInterceptor`/`FoxAuthenticator` for FOX) and
retry-once-then-give-up patterns. Reuse these rather than building new clients; changes to headers/timings here have
broken scrapers before.

## Conventions

- Logging is `log(TAG, message, error?)` from `common/logger/Logger.kt` — slf4j-simple, configured in
  `app/src/main/resources/simplelogger.properties` (its `logFile` is the production path `/root/logs/...`).
- Time uses `DATE_ZONE` (`Europe/Paris`) and the helpers in `ZonedDateTimeExtensions.kt`; `ZonedDateTime.now(DATE_ZONE)`
  rather than bare `now()`.
- API handlers: one method per route on `Api`, body wrapped in try/catch, `context.rateLimit()` first
  (`NaiveRateLimit`, 60 req/min per IP), responses via the `ContextExtensions` helpers
  (`standardResponse`/`notFoundError`/`internalError`).
- Code and comments are English; anything a Discord user or the website sees is French.
- Dependencies go through the `libs.versions.toml` version catalog. Each module repeats the same
  plugins/repositories/jvmToolchain/noArg block — copy an existing `build.gradle.kts` when adding a module, and
  register it in `settings.gradle.kts` plus `app/build.gradle.kts`.
