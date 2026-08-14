# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Kotlin/JVM (Java 17) multi-module Gradle app backing the FulguroGo Discord community. A single process runs many
long-lived background services that scrape/poll the Go servers KGS and OGS, store users and games in MySQL, derive a
custom "Gold" ladder rating/tier per player, notify Discord, and expose a JSON API for the website frontend.

It used to aggregate four more sources — the FOX and IGS servers and the FFG and EGF rating federations — all removed
in 8.8 (`doc/migration remove servers - *.sql`). Nothing of them is left; don't reintroduce a platform-shaped
abstraction on their account.

There are no tests in the repo (`src/test` does not exist anywhere), no linter config, and no CI. Verification means
compiling and running.

## Commands

```bash
./gradlew build                  # compile everything
./gradlew :modules:ogs:build     # compile a single module
./gradlew :app:run               # run the whole app locally (needs config.properties, see below)
./gradlew :app:shadowJar         # fat jar -> app/build/libs/app-<version>-all.jar (ships; do NOT run it locally)
./release.sh                     # prod build: swaps in prod config, shadowJar, moves to export/, restores dev config
```

`fulgurogo.version.name` in `gradle.properties` is the single source of the version (`release.sh` greps it for the
jar name). Bump it there; human-readable release notes go in `doc/changelog.txt` (French).

## Local setup you must know about

`modules/common/src/main/resources/config.properties` is **gitignored** (`*config.properties*`) and the resources
directory is empty in a fresh clone. Nothing runs without it. The expected layout, per `release.sh`, is three files
in that directory: `config.properties.dev`, `config.properties.prod`, and `config.properties` (a copy of one of them
— dev in the working tree). The suffix order matters: `release.sh` copies `config.properties.dev`/`.prod` by exact
name, so `dev.config.properties` or `config.properties.dev.properties` silently break the release build. Only
`config.properties` is on the classpath; the two variants are just templates to copy over it.

`Config.get(key)` reads that file with no defaults and no error handling, so a missing key surfaces as an NPE deep in
a service. Keys currently required include `debug`, `db.*`, `ssh.*`, `bot.*`, `user.agent`,
`global.read.timeout.ms`, `gold.api.port`, `gold.discord.auth.*`, the per-platform blocks (`kgs.archives.url`,
`kgs.game.link`, `kgs.login.*`, `ogs.*`) and `frontend.url`. The league adds `ogs.league.id`, `ogs.league.auth` and
`league.member.salt`, and those three are **identical in dev and prod** — the inverse of every other block, because there
is one OGS league and both environments share it. Three blocks are the exception to "no defaults", all read through
`Config.getOrNull` and all allowed to be empty: `house.period.override` and `league.session.override`, both empty in all
three files, and the four `house.role.*` keys, where an empty value means that house gives its members no Discord role —
see the houses in the data flow below.

⚠ `league.member.salt` is the first key in the project whose **loss destroys business data** rather than taking a service
down. Every player's OGS identity is `sha256(discordId + salt)`, computed and never stored, so changing or losing the salt
re-registers everybody under new member ids and cuts them off from their OGS league history. It lives in a gitignored file
and is therefore outside the database backups: back it up with `bot.token`.

The authoritative values are the ones deployed on the prod server; both variant files hold real credentials in plaintext,
which is why the gitignore pattern has to match every `config.properties*` name.

`ssh.*` is only read when `debug=true` (`DatabaseAccessor` and `App.main` short-circuit on the flag), but keep the
five keys present in the prod file too — with the `useless-only-needed-in-dev` placeholders — so flipping `debug`
against a prod config does not NPE.

When `debug=true`, `App.main` opens an SSH tunnel (`SSHConnector`, jsch, key at `ssh.private.key.file`) because the
server's MySQL only accepts local connections; `DatabaseAccessor` then connects to `ssh.forwarded.port` instead of
`db.port`.

**Run it with `./gradlew :app:run`, or from IntelliJ. Never from the fat jar.** The `run` task pins
`workingDir` to the repository root (`app/build.gradle.kts`) because `ssh.private.key.file` is a path relative to the
root and JavaExec defaults to the subproject — IntelliJ already runs from the root, which is why this only ever broke
from Gradle. The fat jar is worse and fails in a way no working directory fixes: jsch ships as a **multi-release jar**
and shadowJar copies its `META-INF/versions/**` classes without carrying `Multi-Release: true` into the merged
manifest, so the JVM silently uses the Java 8 baseline classes, which have no modern EdDSA — the ed25519 key then
fails as `Auth fail for methods 'publickey,password'` while `ssh -i` with that same key works. Production never
notices because it runs `debug=false` and connects to MySQL directly.

Each failure mode is quiet in its own way, and both look like a broken change rather than a broken launch: no tunnel
means Hikari cannot reach MySQL, so every service dies on its first tick and every DB-backed route answers 500 while
`/gold/api/health` reports the whole registry unhealthy. Check the startup log for
`Forwarded port localhost:… -> localhost:3306` before believing anything else. And do read it on the console:
`simplelogger.properties` points `logFile` at the production path `/root/logs/…`, which does not exist locally, so
slf4j-simple prints a `FileNotFoundException` at startup and then logs to stderr anyway.

If the tunnel is the only thing in the way, `ssh -i app/src/main/resources/id_ed25519 -N -L 9876:localhost:3306
root@<ssh.host>` opens it by hand: `SSHConnector` swallows its own failure and the app connects to
`ssh.forwarded.port` regardless of who opened it.

The tunnel goes to the prod server either way, but the two configs pick different schemas on it: `db.name=fg_dev` in
`config.properties.dev`, `db.name=fg_prod` in `config.properties.prod`. So a local `./gradlew :app:run` writes to
`fg_dev` and cannot corrupt the live ladder — provided you actually copied the dev variant over
`config.properties`, which is the one thing to check before running anything that writes. Only `config.properties` is
read; the filename it came from is invisible at runtime, so `grep db.name` on it is the check, not the file listing.

Two things `fg_dev` is *not*, despite being safe to write to. It is not anonymised: it is a snapshot of `fg_prod`
taken on the same server, so it holds the same **member data** (Discord ids, names, avatars, games) and the same
confidentiality rules apply — nothing out of it goes into a commit, a doc or a log line. And it is not fresh: it
drifts from prod the moment it is taken, so a schema question it answers may be a stale answer.
`mysqldump --no-data --routines fg_prod` still settles those.

Discord is separated the same way: `bot.token`, `bot.guild.id` and `bot.notification.channel.id` all differ between
the two variants, so a dev run drives its own bot on a test server and announces games and promotions there rather
than in the live community. Only `bot.color` and `bot.name` are shared. The isolation of a local run therefore rests
entirely on which file was copied over `config.properties` — get that wrong and both the database and the Discord
channel are the live ones at once.

## `assets/` — local-only scratch folder

Gitignored (`.gitignore:39`), untracked, and referenced by no code or build script. It won't exist in a fresh clone,
so never make code or a Gradle task depend on it. What's in it:

- `fg_prod.dump/` — a `mysqldump` of the production database, one file per table plus `fg_prod_routines.sql` for the
  four views. It is the only copy of the real schema outside the server, so it settles any question `doc/schema.sql`
  leaves open — but it also holds **member data** (Discord ids, names, avatars, games), so nothing from it goes into a
  commit, a doc or a log line. It is a snapshot, not a live mirror: the dump in place predates the 8.8 removal, so it
  still contains `fox_*`, `igs_*`, `ffg_*` and `egf_*` tables and the old view bodies. Handy for a rollback,
  misleading if read as current.
- `kgs-proxy.war` — prebuilt webapp for a proxy that fronted the real KGS API on `localhost:8080`. Dead weight as long
  as `KgsService` scrapes `gameArchives.jsp`; relevant again only if someone goes back to the KGS API.
- `maisons.md` — the "Houses" lore (four houses, their slug, name, tagline, colour), the source `doc/plan-maisons.md`
  seeds from. `icon.xd` is the Adobe XD source for the bot icon.

Earlier notes described `DevConfig.kt`, `ProdConfig.kt` and `GitConfig.kt` here, in the app's pre-`config.properties`
format. They are gone; the credentials live in the three `config.properties*` files described above.

## Architecture

### Module pattern

Every feature is a Gradle module under `modules/` with an identical shape:

- `XModule` — an `object` with a 3-letter `const val TAG` (used by every log line from that module) and an `init()`
  that instantiates and `start()`s its services. `App.main` calls each `init()` in order: aggregators, then community
  modules (gold, fgc, house, league, api — `house` and `league` before `api`, which depends on both), then
  utilities (ping, clean).
- `XService : StalestFirstService<T>` — the work loop (see below; a few services extend `PeriodicFlowService` directly).
- `db/XDatabaseAccessor` — an `object` owning that module's tables via `private const val`; all SQL lives here.
- `db/model/*` — sql2o-mapped data classes.

Dependency edges: `common` is the base (exposes hikari/jsch/jsoup/okhttp/sql2o/coroutines as `api`), `discord`
re-exports `common` plus JDA, platform modules depend on `discord` when they send notifications, and `api` depends on
every other module because it links accounts across all of them.

### The service loop

`PeriodicFlowService(initialDelayInSeconds, intervalInSeconds)` runs `onTick()` on a coroutine flow on
`Dispatchers.IO`. It catches whatever a tick throws, logs it, and ticks again on schedule, so `onTick()` does **not**
need its own catch-all. Only an `Error` (or a failure of the flow itself) still stops a service for good.

Ticks cannot overlap — the flow's `emit` suspends until the collector returns — so do not add a re-entry flag.

Most services extend **`StalestFirstService<T>`** rather than `PeriodicFlowService` directly, implementing three
methods: `stalest()` (the row longest without a refresh), `refresh(stale)`, and `markAsError(stale)`. The base class
routes a `refresh` failure to `markAsError`, which stamps `updated` so a broken row rotates to the back of the queue
instead of blocking it. Extend `PeriodicFlowService` directly only when there is no stalest-row queue to walk
(`CleanService`, `PingService`, `OgsRealTimeService`).

Tick intervals are deliberately staggered (discord 5s, ogs/gold/fgc 15s, house points 30s, kgs 60s,
house season/league session/ping/clean 600s) to spread outbound load. Initial delays stagger the first tick the same
way — the two house services start at 90s and 120s and the league's at 150s, behind `GoldService`, so a cold start does
not open every connection at once.

### Data flow

1. Platform modules fill `<platform>_user_info` (one row per linked Discord user, holds the current rank string like
   `"12k"`/`"3d"`, or `"?"` when unknown) and `<platform>_games`.
2. `GoldService` reads the `gold_ranks` view through `GoldDatabaseAccessor.userRanks()`, and `UserRanks.computeRating()`
   converts each rank to a rating and averages it with hardcoded per-platform weights (KGS 0.8, OGS 1.0). The result is
   matched to a row of `gold_tiers` and written to `gold_ratings`; promotions are announced on Discord.
   The KGS weight is additionally faded by the age of the rank, because KGS publishes no current rank — only the one a
   player held in each archived game, which `KgsService.scrapRank()` reads and dates in `kgs_user_info.kgs_rank_date`.
   Full weight for the first year, then a fifth less per further year, nothing from five years on. Only a *settled*
   rank is stored: KGS marks a rank it is unsure of with a trailing `?` (`"2d?"`), which is what an account that
   drifted while idle gets, and reading one of those as `"2d"` puts a genuine 30k in the middle of the ladder.
   Getting this wrong is not loud: before 8.8 every KGS rank was `"?"`, so the 0.8 weight silently contributed
   nothing at all.
3. `FgcService` counts each player's recent valid games from the `fgc_validity_games` view into `fgc_validity`.
4. The **houses** are a season-long team competition, four houses, a player in at most one. `HousePointsService` walks
   the `house_games` view for games nobody has scored yet and writes a `house_points` row per (game, player) — the
   scale is in `HousePointsCalculator`, and the primary key `(gold_id, discord_id)` is the whole of the idempotence,
   so there is no cursor and nothing to reset. Every row carries its `season` and its `house_id` frozen at write time,
   which is what makes a house's total survive a player leaving it, and why `CleanService` purges `house_members` but
   never `house_points`. `HouseSeasonService` runs the calendar: a season is 1 September to 31 May (`HouseSeason`,
   overridable for dev with `house.period.override`), June to August is the break, and the once-a-year events —
   applying the summer intentions, closing a season, posting the daily ranking — are each guarded by a column of
   `house_seasons` rather than by the calendar, because the calendar cannot say whether something has already been
   done. Announcements go through `HouseNotifier`, and the Discord role that goes with a membership through
   `HouseRoles` — both have the same three callers (the `join` route, and the `CHANGE` and `LEAVE` intentions), which is
   why they sit side by side. The four role ids are config, `house.role.<slug lowercased>`, next to the `bot.*` keys and
   split between dev and prod the same way: a role id only means anything on the guild `bot.guild.id` names, so a local
   run dresses players on the test server by construction. An empty value means the house hands out no role, logged and
   skipped. Granting is best-effort by design — the database is the record of who is in which house, so a disconnected
   bot or a role sitting above the bot's own in the hierarchy costs a log line, never a failed join, and roles can
   therefore drift from `house_members`. Two things have to hold on the guild or every grant fails the same way: the bot
   needs **Manage Roles**, and its own role has to sit **above** the four house roles — `DiscordBot` checks the second
   up front (`canInteract`) and names it in the log, since it is the one nobody guesses.
5. The **league** (`doc/plan-ligue.md`) is a season-long ladder on top of the houses: 16 fortnightly sessions,
   1 September to 31 May, one match per player per session against someone from another house. `LeagueSessionService`
   ticks every 10 minutes and does everything in one pass — draws a session in the 7am-9am window, creates the challenge
   on OGS, DMs each player their invitation link, sweeps OGS for results, settles what was never played, and closes the
   year. Every branch claims a column of `league_sessions` before acting, because a 10-minute tick sees a session start
   about 1400 times and the calendar cannot say what has already been done.

   Four things about it differ from everything else here, and each is a trap:

   - **The OGS league is shared by dev and prod.** There is one, `FulguroGo`, its credentials identical in all three
     config files, and nothing bounds what a local run sends to it. A draw in dev creates *real* matches, permanent
     because `DELETE` answers 405. The `db.name` prefix on `league_match_id` prevents id collisions and nothing more.
   - **OGS notifies nobody.** Creating a match produces no notification of any kind, so the Discord DM carrying the
     invitation link is the only way a player learns they have a match. A DM that fails is an unplayable game — which is
     why `black_notified` / `white_notified` are stamped from inside JDA's success callback, never before, and why the
     links are never deleted.
   - **Results come from OGS's match objects, not from `ogs_games`.** One request per session sweeps
     `GET /matches/?league_match_id__startswith=…`, which also reports annulment — so the league knows a game was voided
     even though the ingestion does not. ⚠ An annulled match still names a loser, so annulment is tested *first*.
   - **A league game scores everywhere**, by construction and not by design effort: 7 renown, the full 11 house points,
     and an FGC-valid game. That is what the game settings in `OgsLeagueClient` exist to guarantee, so none of them is a
     preference — see `doc/ogs-online-league-api.md`, which is the reference for the whole API.
6. `ApiModule` starts Javalin on `gold.api.port`. The players and games routes come almost entirely out of two MySQL
   views, `api_players` and `api_games`. The house routes do not: their figures are counted over the *current* season,
   which only Kotlin knows, so they are hand-written queries in `HouseDatabaseAccessor` and the `house` block of a
   player's profile is composed in the handler rather than added to `api_players` — which also means no view to alter
   on the production server. The league routes are the same story, assembled by `api/league/LeagueApiComposer`, and the league
   adds **no view at all**. ⚠ One rule there is not stylistic: `black_invite` / `white_invite` never leave
   the API, on any route, not even on the profile of the player they belong to — no route here is authenticated, so a
   published player link would let anyone play anyone's match. Only `spectator_link` is public. The other exception is
   `GET /gold/api/health`, which reports the background services:
   200 when they are all healthy, 503 otherwise, so a monitor can watch the status code alone. Every service
   registers itself in `ServiceRegistry` from `PeriodicFlowService.start()`, and each reports whether it is still
   running, how long since its last successful tick, and its consecutive-failure count. A service counts as stale
   after `max(interval * 5, 60s) + initialDelay` without a success — measured from start if it has never had one, so a
   service failing on every tick does not read as healthy.
7. `CleanService` deletes games older than 32 days and invalid accounts, and purges the users Discord confirmed left the
   guild. That purge is **live**, not commented out as it once was: `removeUsersWhoLeft` guards it with `debug` (a dev
   run never acts on departure flags, since in debug the bot cannot tell who is still on the server) and a one-day
   grace period that also covers leave-and-rejoin. It is destructive and has no undo — a purged user loses their
   Discord row, their platform links, their rating, their fgc validity and their house membership, and has to link
   everything again. Losing the membership is deliberate: it is the only way out of a house mid-season, because the API
   only offers "leave" during the summer break. `house_points` is pointedly **not** purged, or a house's total would
   shrink when someone left.

Rank/rating math is centralized in `common/utilities/RankingUtilities.kt` (`ratingToRank`, `rankToRating`,
`rankToKyuDanString`, `kyuDanStringToRank`) — don't reimplement conversions locally. Likewise
`String.sgfProperty(key)` in `common/utilities/SgfExtensions.kt` for reading `SZ`/`HA`/`KM`/`TM` out of an SGF, and
`discord/GameNotifier.notify(game, server)` for announcing a game (implement `NotifiableGame` on the game model).

`DiscordBot` sends to a channel with `sendMessageEmbeds` and to a person with `sendPrivateMessageEmbeds`. The second takes
an `onSuccess` callback rather than answering a boolean, and that shape is load-bearing: `queue()` is asynchronous, so a
synchronous answer could only ever mean "queued", never "delivered". The league records a link as sent from inside that
callback. Both are best-effort and neither throws — every failure is a log line, as with `modifyRole`, and each keeps a
synchronous `try/catch` because JDA validates on the calling thread and can throw before anything is queued.

### Database

sql2o over a HikariCP pool, one shared `DatabaseAccessor.dao`, always used as
`DatabaseAccessor.withDao { connection -> ... }` (opens and closes a connection per call; leak detection is at 5s).
Queries are hand-written strings with named parameters.

- **Build queries with `connection.query(sql)`, not `createQuery(sql)`.** The `query` extension in
  `common/db/DatabaseAccessor.kt` sets `setAutoDeriveColumnNames(true)`, which maps `snake_case` columns onto
  `camelCase` properties automatically, so a new column needs no registration anywhere. Plain `createQuery` skips the
  derivation and — because most reads also set `throwOnMappingFailure(false)` — silently maps such a column to null.
- **Models need a no-arg constructor.** Data classes are annotated `@GenerateNoArgConstructor`; the
  `kotlin-noarg` plugin is applied by the `fulgurogo-module` convention plugin in `buildSrc`.

There is no migration tool. `doc/schema.sql` is the reference schema (13 tables, plus the `gold_ranks`,
`fgc_validity_games`, `api_players`, `api_games`, `house_games` views and the `gold_tiers` rows) and it may have
drifted from the live database — `mysqldump --no-data --routines fg_prod` settles any doubt. Schema and view changes
are applied by hand on the server, and each one is written down as a `doc/migration *.sql` script stating where in a
deploy it goes.

`doc/schema.sql` deliberately does not carry the RP text of the four `houses` rows — taglines, colours, descriptions.
`doc/migration maisons.sql` is the single authority for that, and the reference schema only lists the ids and slugs.

### Cross-platform game identity

Games are keyed by `gold_id`, formatted `OGS_<id>` / `KGS_<black>_<white>_<epochMillis>` (KGS has no
game id, hence the composite). Games are inserted the first time they're seen — including while still
`result = "unfinished"`, so Discord can announce games in progress — then updated by `finishGame()` when a result
appears. Views filter `result != "unfinished"` so unfinished games never reach the API.

Storing and announcing games goes through `discord/GameReconciler.kt` — `reconcileGames(games, store, server)`, or
`reconcileGame` for a single one. Each platform's accessor implements `GameStore`, whose `addGame`/`finishGame` are
idempotent (`INSERT IGNORE`, and `UPDATE ... WHERE result = 'unfinished'`) and return whether *this* call changed the
row. Only the winning writer notifies, so "announce once" is enforced by the `gold_id` primary key rather than by
assuming a single writer.

OGS has two paths that write the same table: `OgsService` polls the REST API for user profiles and finished games,
while `OgsRealTimeService` keeps a WebSocket open (auth via `ogs.auth.*`, ping every 10s, `gamelist/query` filtered
to known player ids, then `game/connect` per game). That second writer is exactly why the idempotent contract exists —
a bug where a correspondence game overwrote live results was fixed here — so be deliberate about which game a
`gold_id` refers to when touching either file.

`OgsService` is also the only path that **deletes**: `OgsDatabaseAccessor.removeAnnulledGames` drops the games OGS has
since voided, and their `house_points` rows with them. It has to be there, because only the REST poll ever sees
`annulled` — `OgsWsGameData` has no such field, so the WebSocket writes a game it cannot know was later voided, which is
the usual order of events. Skipping annulled games at ingestion, which is all this used to do, left a voided game stored
as a real win: it happened, and FGC counted it. Two consequences worth knowing before touching it. Deleting from
`house_points` is a deliberate exception to "the register is never purged" — that rule protects a house total from a
player *leaving*, not from points never earned — and it cannot be replaced by finding orphans later, since
`CleanService` deletes every game after 32 days and "points whose game is gone" would then mean the whole register. And
the fix only reaches as far as the poll window: a game annulled after it has left `ogs_games` stays counted.

### Outbound HTTP etiquette

Scraping is fragile and rate-sensitive. `common/utilities/HttpUtilities.kt` centralizes the okhttp client and the
jsoup `scrap(url)` helper, which sends a full browser-like header set and the configured `user.agent`. Services add
their own throttling (`ensureSpamDelay()` in `KgsService`) and retry-once-then-give-up patterns. Reuse these rather
than building new clients; changes to headers/timings here have broken scrapers before.

`gameArchives.jsp` is **behind a KGS login** — a servlet form login, `user`/`password` posted to `login.jsp`, session
kept in a `JSESSIONID` cookie. `KgsSession` owns it: it scraps, notices a login page, authenticates and retries once,
so the session lifetime never has to be known and an expired one costs a single wasted request. One login covers every
archive page whichever player and month, KGS keeps the same `JSESSIONID` across the login rather than reissuing it, and
several sessions on one account coexist, so a dev run and prod can share the `kgs.login.user` account. Jsoup holds no
session state of its own and the `okHttpClient` cookie jar is a different stack, hence the cookie map threaded through
`scrap`/`postForm` by hand. The SGF files on `files.gokgs.com` are *not* gated, which is why `fetchSgf` still needs
none of this.

Detection has to read the body: the wall answers **200** with a login page, and so does a rejected password. Left
unnoticed that parses as an archive page with no games table, and a tick "succeeds" having imported nothing and set
every rank back to `'?'` — the same shape of silent failure as the French-page bug below. `KgsSession` keys on
`form[action*=login.jsp]` rather than on the page title, and throws when a login does not take.

`Accept-Language` is the sharpest of those edges and is now English. gokgs.com honours it, and the French value it
used to carry (for the FFG and EGF sites, both gone) made it serve French archive pages, where `KgsService` dropped
every game row on the date format — no error, no log line, just an empty `kgs_games` and `kgs_rank = '?'` for
everyone, for years. A French page also writes a white win `B+`, for *Blanc*, which the parser reads as black. Ask
for English; do not teach the parser French.

## Conventions

- Logging is `log(TAG, message, error?)` from `common/logger/Logger.kt` — slf4j-simple, configured in
  `app/src/main/resources/simplelogger.properties` (its `logFile` is the production path `/root/logs/...`).
- Time uses `DATE_ZONE` (`Europe/Paris`) and the helpers in `ZonedDateTimeExtensions.kt`; `ZonedDateTime.now(DATE_ZONE)`
  rather than bare `now()`.
- API handlers: one method per route on `Api`, written as `context.handle("routeName") { ... }`. That wrapper applies
  the rate limit (`NaiveRateLimit`, 60/min per IP+method+route) *before* the catch, since it signals by throwing, and
  maps anything unexpected to a 500. Responses go through the `ContextExtensions` helpers
  (`standardResponse`/`notFoundError`/`internalError`).
- Code and comments are English; anything a Discord user or the website sees is French.
- Dependencies go through the `libs.versions.toml` version catalog. A new module's `build.gradle.kts` is just
  `plugins { id("fulgurogo-module") }` plus its own dependencies — the convention plugin in `buildSrc` supplies
  plugins/repositories/jvmToolchain/noArg. Register it in `settings.gradle.kts` plus `app/build.gradle.kts`.
