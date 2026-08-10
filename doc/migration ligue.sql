-- League ("Ligue d'Aurak"): the season-long ladder of paired fortnightly matches. Step 0 of `doc/plan-ligue.md`.
--
-- APPLY THIS ANY TIME BEFORE DEPLOYING. Everything here is purely additive -- six new tables, no view, and not one
-- change to any existing table or to the five live views -- so the running jar keeps working unchanged with these
-- objects in place. The jar that needs them is the one that ships LeagueModule; until then they just sit there empty.
--
-- Test it on fg_dev first, then apply the same file to fg_prod at deploy time:
--
--   mysql -u <db.user> -p fg_dev  < "doc/migration ligue.sql"
--   mysql -u <db.user> -p fg_prod < "doc/migration ligue.sql"
--
-- Run it as the DB user that owns the existing views (db.user in config.properties), so that anything added here later
-- keeps the same owner as its neighbours.
--
-- Every statement is re-runnable AND non-destructive, which is not the same thing. `doc/plan-ligue.md` writes the
-- tables as DROP TABLE IF EXISTS + CREATE TABLE; that is re-runnable, but re-running it mid-season would silently
-- delete every match played so far, every academy membership and the whole standings. CREATE TABLE IF NOT EXISTS is
-- used instead, exactly as in `migration maisons.sql`: a second run is a no-op on an existing table. The trade-off is
-- that it will NOT pick up a later change to a column -- if one of these definitions ever changes, that needs its own
-- ALTER in its own migration file, not an edit here.
--
-- Two deviations from the plan, both deliberate and both stated where they apply: the integers are INT(11) rather than
-- INT, to read like the other 13 tables (identical type either way -- the display width has meant nothing since MySQL
-- 8.0 deprecated it), and ROW_FORMAT = DYNAMIC is stated on all four tables whose primary key contains a
-- VARCHAR(255), not just on the two the plan flagged. See the note on `league_members`.

-- ---------------------------------------------------------------------------------------------------------------
-- Tables
--
-- Six, and the split between them is about lifetime, not about normalisation:
--
--   league_seasons     once-a-season guard        one row per season
--   league_sessions    once-a-session guard       one row per session, created by its draw
--   league_players     the OGS side, for life     one row per player, never purged by the season
--   league_members     the academy, per season    one row per player per season
--   league_matches     the pairings and results   one row per match; the standings are derived from it alone
--   league_exemptions  the players left unpaired  one row per player per session they were not paired in
--
-- ROW_FORMAT is stated on the four whose PK contains a VARCHAR(255), which is 1020 bytes in utf8mb4 and therefore over
-- the 767-byte index limit of COMPACT. It is belt-and-braces: `house_members` has had a 1020-byte PK in production
-- since the Houses shipped without stating anything, so this server's default row format is already DYNAMIC. If one of
-- these still fails with error 1071, the server has innodb_large_prefix off and the fix is a narrower `discord_id`,
-- not another ROW_FORMAT -- a Discord snowflake is 20 characters, so VARCHAR(255) is already 12x more than it needs.
-- ---------------------------------------------------------------------------------------------------------------

-- The once-only guard for what must happen exactly once per season: opening it, and announcing the closing recap.
-- Same job as `house_seasons`, and the same reason for existing rather than being deduced -- the service ticks every
-- ten minutes, so a calendar cannot say whether something has already been done.
--
-- No column for the OGS league. There is only one, `FulguroGo`, it is permanent, it crosses the seasons, and its
-- identifiers live in `ogs.league.id` / `ogs.league.auth` in config.properties. What 1 September triggers on this side
-- is the emptying of the academies, which is free: see `league_members`.
--
-- `season` is the '2026-2027' string computed in Kotlin, hence VARCHAR(9), and `opened IS NULL` means the same thing
-- as it does for the houses -- a row exists but the season never started, which is what tells a real closing apart
-- from a first deploy during the summer.
CREATE TABLE IF NOT EXISTS `league_seasons` (
  `season` VARCHAR(9) NOT NULL,
  `opened` DATETIME NULL,
  `closed` DATETIME NULL,
  PRIMARY KEY (`season`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- Three guards, one per event in the life of a session, and each one is what makes that event happen once.
--
-- `drawn` is the important one: a service ticking every ten minutes sees the start of a session about 1400 times, and
-- this column is what says the draw has already run -- not the calendar. `notified` separates the announcement from
-- the draw, so a Discord failure costs neither a second draw nor the announcement. `settled` marks the settlement,
-- where the matches still unfinished become permanently void; that is not an operation to run twice.
--
-- A row is created by the draw, so a session that has not been drawn has no row at all. That is the right state for a
-- session still to come, and it is a readable difference from a session drawn with no match in it.
CREATE TABLE IF NOT EXISTS `league_sessions` (
  `season` VARCHAR(9) NOT NULL,
  `session` INT(11) NOT NULL,
  `drawn` DATETIME NULL,
  `notified` DATETIME NULL,
  `settled` DATETIME NULL,
  PRIMARY KEY (`season`, `session`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- The OGS side, permanent and with no season: registering a member with OGS cannot be undone, and the brief asks
-- explicitly that a player stay in the OGS league after leaving their academy. Two tables rather than one because the
-- two memberships do not have the same lifetime, and an `active` per season mixed in with a for-life state is exactly
-- the kind of column that eventually gets purged by mistake.
--
-- Two columns, and not three. There is NO `ogs_member_id`: the member id is `sha256(discord_id + league.member.salt)`,
-- computed on the spot by the single function that knows how, never stored, and therefore impossible to desynchronise.
-- And no rating column, because the rating pushed to OGS is one constant for everybody -- a column carrying the same
-- value on every row would say nothing `ogs_registered` does not already say.
--
-- `ogs_registered IS NULL` is the work queue: the row is written by the join route, and the `PUT member/{id}` call is
-- left to the tick, so that an inscription never fails because OGS is momentarily down.
CREATE TABLE IF NOT EXISTS `league_players` (
  `discord_id` VARCHAR(255) NOT NULL,
  `ogs_registered` DATETIME NULL,
  PRIMARY KEY (`discord_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 ROW_FORMAT = DYNAMIC;

-- The academy, per season. The season being in the PK is what empties the academies for free: on 1 September the new
-- season simply has no rows, and the previous one stays readable. No purge to write, so no purge to get wrong.
--
-- `active` rather than a delete: a player who leaves keeps their matches and their renown visible, and can come back.
-- A join after a leave finds the existing row and flips `active` back to 1 without restamping `joined`.
--
-- The house is NOT here. `house_members` is its source, and duplicating it would create a second truth. It is frozen
-- on the match instead, where it actually matters, and not at inscription time.
--
-- ROW_FORMAT is explicit here even though the plan does not ask for it: this PK is 1056 bytes in utf8mb4 -- 36 for the
-- season plus 1020 for the id -- which is the same side of the COMPACT limit as the two tables the plan does flag. The
-- asymmetry was an oversight in the plan, not a judgement, and stating it costs nothing.
CREATE TABLE IF NOT EXISTS `league_members` (
  `season` VARCHAR(9) NOT NULL,
  `discord_id` VARCHAR(255) NOT NULL,
  `joined` DATETIME NOT NULL,
  `active` TINYINT(1) NOT NULL DEFAULT 1,
  `left_since` DATETIME NULL,
  PRIMARY KEY (`season`, `discord_id`),
  KEY `league_members_active` (`season`, `active`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 ROW_FORMAT = DYNAMIC;

-- The pairings, and the only source of the standings -- there is no points table. A match carries its two players,
-- their house frozen at draw time and its result, so the renown is entirely derivable from this one table. A separate
-- register would be a second truth to keep in agreement with the first, and the PK already provides the idempotence
-- a register would have been asked to provide.
--
-- No AUTO_INCREMENT, like everywhere else in this schema. The PK and the unique key on white state the real rule of
-- the domain -- at most one match per player per session, whichever side they are on -- and that is what makes a draw
-- run twice harmless: the second insert lands on a key rather than on a duplicate row.
--
-- `league_match_id` is what we push to OGS, derived from the PK and therefore deterministic. It is OGS's idempotence
-- key: two attempts at creating the same match send the same id, and the second returns the first (200 instead of
-- 201) rather than creating another one. It is prefixed with the database name --
-- '<db.name>_<season>_<session>_<black_discord_id>', so 'fg_dev_2026-2027_8_236813095207436289' -- which is a direct
-- consequence of there being a single OGS league shared by dev and prod: without the prefix, a dev draw and a prod
-- draw that pair the same two players on the same session with the same player as black would send the SAME
-- league_match_id to the SAME league. The test accounts are real members of the community, so they will be drawn in
-- production too; the collision is expected, not theoretical. Under 64 characters with room to spare.
--
-- `black_house_id` / `white_house_id` are frozen at write time, for the same reason as in `house_points`: an academy's
-- total must not move when a player changes house or leaves it.
--
-- `ogs_match_id` is an INT because an OGS match id is one, and it is indexed because THE CALLBACK ARRIVES ON IT --
-- OGS calls `game_update/{id}` with the id of the match.
--
-- The three links are never cleared, contrary to what the brief suggested ("saved for the duration of the session"):
-- they are the only way to resend a link to a player whose DM failed, and that resend is done by hand, possibly days
-- later. `black_notified` / `white_notified` date the DM that went out, which is what answers "who did not get their
-- link?" without rereading the logs.
--
-- `gold_id` ('OGS_<ogs_game_id>') is the bridge to `ogs_games`, the key the rest of the application already knows. No
-- foreign key, on purpose: CleanService deletes games after 32 days and a November match has to stay readable in May,
-- which is also why `result` is copied into the row rather than read through a join.
--
-- `result` has THREE families of values, and that is the heart of the "not played, not replayable" rule: NULL while
-- the fate of the match is still open, the winner OGS names once it has been played, and 'unplayed' as soon as the
-- session has been settled without a result arriving. 'unplayed' is terminal -- no write looks at it again -- which is
-- exactly what makes a game played late on OGS have no effect on the league. The settlement leaves NO match at NULL,
-- and that is what closes the nastiest failure mode of this module: a match pending forever is neither played nor
-- exempted, so it silently costs both players the perfect-attendance bonus, and that only surfaces in May.
--
-- ROW_FORMAT is explicit because that PK is 1060 bytes in utf8mb4 -- 36 for the season, 4 for the session, 1020 for
-- the id -- fine under DYNAMIC, too wide for COMPACT. (The plan says 2040; that figure is `house_points`'s, which has
-- two VARCHAR(255) in its PK. The conclusion is the same either way.)
CREATE TABLE IF NOT EXISTS `league_matches` (
  `season` VARCHAR(9) NOT NULL,
  `session` INT(11) NOT NULL,
  `black_discord_id` VARCHAR(255) NOT NULL,
  `white_discord_id` VARCHAR(255) NOT NULL,
  `black_house_id` INT(11) NOT NULL,
  `white_house_id` INT(11) NOT NULL,
  `pairing_score` DOUBLE NOT NULL,
  `league_match_id` VARCHAR(64) NOT NULL,
  `ogs_match_id` INT(11) NULL,
  `black_invite` VARCHAR(255) NULL,
  `white_invite` VARCHAR(255) NULL,
  `spectator_link` VARCHAR(255) NULL,
  `black_notified` DATETIME NULL,
  `white_notified` DATETIME NULL,
  `ogs_game_id` INT(11) NULL,
  `gold_id` VARCHAR(255) NULL,
  `result` VARCHAR(255) NULL,
  `created` DATETIME NOT NULL,
  `finished` DATETIME NULL,
  PRIMARY KEY (`season`, `session`, `black_discord_id`),
  UNIQUE KEY `league_matches_white` (`season`, `session`, `white_discord_id`),
  UNIQUE KEY `league_matches_league_id` (`league_match_id`),
  KEY `league_matches_ogs_match` (`ogs_match_id`),
  KEY `league_matches_gold_id` (`gold_id`),
  KEY `league_matches_season_black` (`season`, `black_discord_id`),
  KEY `league_matches_season_white` (`season`, `white_discord_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 ROW_FORMAT = DYNAMIC;

-- The players a draw left without an opponent. This is the only thing that makes the perfect-attendance bonus
-- computable: without it, a session with no match for a player is indistinguishable from a session where they were
-- not there, and the bonus would be wrong in the way that shows least -- a diligent player losing it without
-- understanding why.
--
-- Written by the draw, in the same logical transaction as the matches, and NEVER by the settlement: an exemption is a
-- decision of the draw, not a consequence of a match not being played.
--
-- `reason` is 'ODD' (the active roster was odd and one player was left over) or 'NO_RIVAL' (every other active player
-- was of their own house). No code reads this column. It exists for the question "why was I not drawn?", which will be
-- asked, and which a SELECT should be able to answer without rereading January's logs. VARCHAR rather than an ENUM so
-- that a third reason stays a code change, not a DDL change on prod.
--
-- The PK does the idempotence, as everywhere else here: a draw run again adds nothing.
CREATE TABLE IF NOT EXISTS `league_exemptions` (
  `season` VARCHAR(9) NOT NULL,
  `session` INT(11) NOT NULL,
  `discord_id` VARCHAR(255) NOT NULL,
  `reason` VARCHAR(32) NOT NULL,
  `created` DATETIME NOT NULL,
  PRIMARY KEY (`season`, `session`, `discord_id`),
  KEY `league_exemptions_player` (`season`, `discord_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 ROW_FORMAT = DYNAMIC;

-- ---------------------------------------------------------------------------------------------------------------
-- Views
--
-- None, and nothing to change on the five that exist.
--
-- The standings depend on the current season, which only Kotlin knows, and a view cannot be given a parameter. The
-- aggregates are therefore hand-written queries in `LeagueDatabaseAccessor`, the same choice the house routes made
-- for the same reason. The upside on the day of the deploy is that there is no view to swap on the production server.
-- ---------------------------------------------------------------------------------------------------------------

-- ---------------------------------------------------------------------------------------------------------------
-- The OGS callback, to register by hand
--
-- Not SQL, and not in the application code either. It belongs here because it is the other thing that has to be done
-- by hand on the server, once, and forgetting it is silent: the results would then only ever arrive through the
-- catch-up poll.
--
-- There is one league and therefore ONE `callback_url_template`, a setting global to the league, and it points at the
-- production server.
--
-- First read what is in place. The call exists, and changes nothing:
--
--   curl https://online-go.com/api/v1/online_league/callback \
--     -H "X-OGS-LEAGUE: <ogs.league.id>" -H "X-OGS-LEAGUE-AUTH: <ogs.league.auth>"
--
-- On 10 August 2026 it answered {"callback_url_template": null} -- nothing registered yet.
--
-- Then register it, ONCE, FROM PRODUCTION:
--
--   curl -X PUT https://online-go.com/api/v1/online_league/callback \
--     -H "X-OGS-LEAGUE: <ogs.league.id>" -H "X-OGS-LEAGUE-AUTH: <ogs.league.auth>" \
--     -H "Content-Type: application/json" \
--     -d '{"callback_url_template":"https://<our-host>/gold/api/league/game_update/{id}"}'
--
-- Two conditions, both easy to miss:
--
--   * Only AFTER the route of step 8 is deployed. OGS tests the URL at registration time with id=0 and requires a
--     200, so `game_update/0` has to answer 200 while finding nothing in the database.
--   * NEVER from a dev machine. The template is global to the league and the league is shared, so repointing it at an
--     unreachable localhost would cut production's callbacks, and production would only get its results through the
--     catch-up. That is the whole reason this call is not in the code.
--
-- In dev, no callback ever arrives. The catch-up of step 8 is what makes the league testable locally, which is also
-- why it cannot be treated as a secondary path.
--
-- The credentials are NOT in this file. It is tracked by git; `ogs.league.auth` is an API key and lives in
-- config.properties, which is gitignored precisely for that -- like `bot.token`, `db.password` and
-- `kgs.login.password`. Substitute the two headers from that file when running the commands above.
-- ---------------------------------------------------------------------------------------------------------------

-- ---------------------------------------------------------------------------------------------------------------
-- Checks
--
-- The first must return 6 rows: league_exemptions, league_matches, league_members, league_players, league_seasons,
-- league_sessions.
--
--   SHOW TABLES LIKE 'league_%';
--
-- The second must return Dynamic on the four tables keyed on a VARCHAR(255). A Compact there means the server ignored
-- the clause, and the wide indexes are living on innodb_large_prefix rather than on the row format.
--
--   SELECT TABLE_NAME, ROW_FORMAT FROM information_schema.TABLES
--     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME LIKE 'league\_%' ORDER BY TABLE_NAME;
--
-- The third must return 6 tables and 0 views, in the schema you meant to change and not the one next door:
--
--   SELECT TABLE_NAME, TABLE_TYPE FROM information_schema.TABLES
--     WHERE TABLE_SCHEMA = 'fg_dev' AND TABLE_NAME LIKE 'league\_%';
--
-- The fourth must return 0 on a fresh install, and stays 0 until someone joins an academy. It is the eligibility join
-- of the draw, minus `league_players`, so a non-zero answer before any deploy means one of the four tables it reads
-- is not what this file created.
--
--   SELECT COUNT(*) FROM `league_members` AS `m`
--     JOIN `house_members` AS `h` ON `h`.`discord_id` = `m`.`discord_id`
--     JOIN `ogs_user_info` AS `o` ON `o`.`discord_id` = `m`.`discord_id`
--     JOIN `gold_ratings` AS `g` ON `g`.`discord_id` = `m`.`discord_id`
--     WHERE `m`.`active` = 1 AND `g`.`rating` > 0 AND `g`.`error` = 0;
-- ---------------------------------------------------------------------------------------------------------------
