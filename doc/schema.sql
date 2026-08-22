-- Reference schema of the fg_prod database: 19 tables, 5 views, and the gold_tiers reference rows.
--
-- There is no migration tool. The live schema is changed by hand on the server, and this file is what the code
-- expects to find there -- so it is a reference, not a script anyone runs, and it can drift. When in doubt the
-- server wins; `mysqldump --no-data --routines fg_prod` is how you check.
--
-- Reads go through sql2o with setAutoDeriveColumnNames(true), so a column maps onto the camelCase property of the
-- same name and nothing has to be registered anywhere. The flip side is that most reads also set
-- throwOnMappingFailure(false): rename a column and the property silently turns null instead of failing. The four
-- views below therefore have to stay aligned, column for column, with the models that read them:
--
--   gold_ranks         -> gold/db/model/UserRanks
--   api_players        -> api/db/model/ApiDbPlayer
--   api_games          -> api/db/model/ApiDbGame
--   fgc_validity_games -> fgc/db/model/FgcValidityGame
--   house_games        -> house/db/model/HouseGame
--
-- Only KGS and OGS are aggregated. FOX, IGS, FFG and EGF were removed in 8.8 -- see
-- `migration remove servers - 1 before deploy.sql` and `- 2 after deploy.sql` for the change that got us here.
--
-- The four house_* tables and the house_games view come from `migration maisons.sql`, which is where their
-- design is argued at length. They are additive and were applied ahead of the jar that uses them, so on a server
-- that has run that script but not yet deployed the Houses they exist and stay empty.
--
-- The six league_* tables come from `migration ligue.sql`, same story and same authority for the reasoning. They add
-- no view: the standings depend on the current season, which only Kotlin knows, so they are hand-written queries in
-- LeagueDatabaseAccessor. Nothing below them reads a league table, so this block is additive in both directions.

-- ---------------------------------------------------------------------------------------------------------------
-- Tables
-- ---------------------------------------------------------------------------------------------------------------

-- Discord OAuth credentials of a website visitor, keyed on their Discord id.
CREATE TABLE `auth_credentials` (
  `gold_id` VARCHAR(255) NOT NULL,
  `access_token` VARCHAR(255) NULL,
  `token_type` VARCHAR(255) NULL,
  `refresh_token` VARCHAR(255) NULL,
  `expiration_date` DATETIME NULL,
  PRIMARY KEY (`gold_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- One row per member of the Discord server. `left_server_since` dates a departure; CleanService uses it.
CREATE TABLE `discord_user_info` (
  `discord_id` VARCHAR(255) NOT NULL,
  `discord_name` VARCHAR(255) NOT NULL,
  `discord_avatar` VARCHAR(255) NOT NULL,
  `updated` DATETIME NULL,
  `error` TINYINT(1) NOT NULL,
  `left_server_since` DATETIME NULL,
  PRIMARY KEY (`discord_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- One row per Discord user who linked a KGS account. `kgs_rank` holds a kyu/dan string ("12k", "3d") or "?" when
-- unknown, and `kgs_rank_date` is the date of the archived game that rank was read from: KGS publishes no current
-- rank, so it can be years old and UserRanks.computeRating fades the KGS weight accordingly.
CREATE TABLE `kgs_user_info` (
  `discord_id` VARCHAR(255) NOT NULL,
  `kgs_id` VARCHAR(255) NOT NULL,
  `kgs_rank` VARCHAR(255) NOT NULL,
  `kgs_rank_date` DATETIME NULL,
  `updated` DATETIME NULL,
  `error` TINYINT(1) NOT NULL,
  PRIMARY KEY (`discord_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- KGS has no game id, so `gold_id` is the composite KGS_<black>_<white>_<epochMillis>. Games are inserted while
-- still `result = 'unfinished'` so Discord can announce them, then updated once a result appears.
CREATE TABLE `kgs_games` (
  `gold_id` VARCHAR(255) NOT NULL,
  `date` DATETIME NOT NULL,
  `black_id` VARCHAR(255) NOT NULL,
  `black_rank` VARCHAR(255) NOT NULL,
  `white_id` VARCHAR(255) NOT NULL,
  `white_rank` VARCHAR(255) NOT NULL,
  `size` INT(11) NOT NULL,
  `komi` DOUBLE NOT NULL,
  `handicap` INT(11) NOT NULL,
  `ranked` TINYINT(1) NOT NULL,
  `long_game` TINYINT(1) NOT NULL,
  `result` VARCHAR(255) NOT NULL,
  `sgf` TEXT NOT NULL,
  PRIMARY KEY (`gold_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- `ogs_id` is the one platform id column that really is an INT.
CREATE TABLE `ogs_user_info` (
  `discord_id` VARCHAR(255) NOT NULL,
  `ogs_id` INT(11) NOT NULL,
  `ogs_name` VARCHAR(255) NOT NULL,
  `ogs_rank` VARCHAR(255) NOT NULL,
  `updated` DATETIME NULL,
  `error` TINYINT(1) NOT NULL,
  PRIMARY KEY (`discord_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- One immutable snapshot per Discord user who linked a FOX account. The public fox-go-api resolves the typed username
-- to the canonical UID, name and rank at link time; this first integration deliberately has no refresh service.
CREATE TABLE `fox_user_info` (
  `discord_id` VARCHAR(255) NOT NULL,
  `fox_id` VARCHAR(255) NOT NULL,
  `fox_name` VARCHAR(255) NOT NULL,
  `fox_rank` VARCHAR(255) NOT NULL,
  PRIMARY KEY (`discord_id`),
  UNIQUE KEY `fox_user_info_fox_id_uq` (`fox_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- `gold_id` is OGS_<id>. Written by two services -- OgsService polling the REST API and OgsRealTimeService on its
-- WebSocket -- which is why GameStore's addGame/finishGame are idempotent and only the winning writer notifies.
CREATE TABLE `ogs_games` (
  `gold_id` VARCHAR(255) NOT NULL,
  `id` INT(11) NOT NULL,
  `date` DATETIME NOT NULL,
  `black_id` INT(11) NOT NULL,
  `black_name` VARCHAR(255) NOT NULL,
  `black_rank` VARCHAR(255) NOT NULL,
  `white_id` INT(11) NOT NULL,
  `white_name` VARCHAR(255) NOT NULL,
  `white_rank` VARCHAR(255) NOT NULL,
  `size` INT(11) NOT NULL,
  `komi` DOUBLE NOT NULL,
  `handicap` INT(11) NOT NULL,
  `ranked` TINYINT(1) NOT NULL,
  `long_game` TINYINT(1) NOT NULL,
  `result` VARCHAR(255) NOT NULL,
  `sgf` TEXT NOT NULL,
  PRIMARY KEY (`gold_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- The Gold ladder itself: the rating GoldService derives from gold_ranks, and the tier it falls into.
CREATE TABLE `gold_ratings` (
  `discord_id` VARCHAR(255) NOT NULL,
  `rating` DOUBLE NOT NULL,
  `tier_rank` INT(11) NOT NULL,
  `updated` DATETIME NULL,
  `error` TINYINT(1) NOT NULL,
  PRIMARY KEY (`discord_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- Rating brackets, `min` inclusive and `max` exclusive. Reference data, seeded below.
CREATE TABLE `gold_tiers` (
  `rank` INT(11) NOT NULL,
  `name` VARCHAR(255) NOT NULL,
  `min` INT(11) NOT NULL,
  `max` INT(11) NOT NULL,
  PRIMARY KEY (`rank`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- Recent game counts per player, filled by FgcService from the fgc_validity_games view.
CREATE TABLE `fgc_validity` (
  `discord_id` VARCHAR(255) NOT NULL,
  `total_games` INT(11) NOT NULL,
  `total_ranked_games` INT(11) NOT NULL,
  `gold_games` INT(11) NOT NULL,
  `gold_ranked_games` INT(11) NOT NULL,
  `updated` DATETIME NULL,
  `error` TINYINT(1) NOT NULL,
  PRIMARY KEY (`discord_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- The four houses and their RP. `slug` is the machine key the API exposes and the website builds the crest name
-- from; `name` is display-only. `color` includes the leading '#', hence VARCHAR(7). Reference data, seeded below.
CREATE TABLE `houses` (
  `id` INT(11) NOT NULL,
  `slug` VARCHAR(64) NOT NULL,
  `name` VARCHAR(255) NOT NULL,
  `tagline` VARCHAR(255) NOT NULL,
  `color` VARCHAR(7) NOT NULL,
  `description` TEXT NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `houses_slug` (`slug`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- One house at most per player, which is what the PK enforces; leaving a house is deleting the row. `joined` stops
-- retroactive scoring -- the scanner only credits games dated at or after it. `pending_action` is a summer
-- intention (NULL, 'STAY', 'CHANGE', 'LEAVE') that HouseSeasonService applies and clears when the season opens.
-- `pending_house_id` is where a 'CHANGE' goes: players pick their own house, there is no draw to make one up, so a
-- 'CHANGE' with a NULL here cannot be applied and the member stays put. NULL on every other intention, written and
-- cleared with `pending_action` in the same statement. No foreign key, like everywhere else in these five tables.
CREATE TABLE `house_members` (
  `discord_id` VARCHAR(255) NOT NULL,
  `house_id` INT(11) NOT NULL,
  `joined` DATETIME NOT NULL,
  `pending_action` VARCHAR(16) NULL,
  `pending_house_id` INT(11) NULL,
  PRIMARY KEY (`discord_id`),
  KEY `house_members_house` (`house_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- The points register: one row per (game, player), broken down per bonus type. The PK is the whole of the
-- scanner's idempotence, so it needs no cursor and no "scored" flag. `house_id` and `season` are frozen at write
-- time, so a house total only ever grows and the history needs no extra table. No foreign key on purpose:
-- CleanService deletes games after 32 days and members when they leave the Discord server, and these rows have to
-- outlive both.
-- ROW_FORMAT is explicit because that PK is 2040 bytes in utf8mb4 -- fine under DYNAMIC, too wide for COMPACT.
CREATE TABLE `house_points` (
  `gold_id` VARCHAR(255) NOT NULL,
  `discord_id` VARCHAR(255) NOT NULL,
  `house_id` INT(11) NOT NULL,
  `season` VARCHAR(9) NOT NULL,
  `played` INT(11) NOT NULL,
  `gold_opponent` INT(11) NOT NULL,
  `rival_house` INT(11) NOT NULL,
  `long_game` INT(11) NOT NULL,
  `victory` INT(11) NOT NULL,
  `even_game` INT(11) NOT NULL,
  `ranked` INT(11) NOT NULL,
  `scored_at` DATETIME NOT NULL,
  PRIMARY KEY (`gold_id`, `discord_id`),
  KEY `house_points_season_house` (`season`, `house_id`),
  KEY `house_points_season_player` (`season`, `discord_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 ROW_FORMAT = DYNAMIC;

-- The once-only guard for what must happen exactly once a season: applying the summer intentions, the closing
-- recap, the daily ranking. `season` is the '2026-2027' string computed in Kotlin. `opened IS NULL` means the
-- season has a row but never started, which also tells a real closing apart from a first deploy during the summer.
CREATE TABLE `house_seasons` (
  `season` VARCHAR(9) NOT NULL,
  `opened` DATETIME NULL,
  `closed` DATETIME NULL,
  `last_ranking` DATETIME NULL,
  PRIMARY KEY (`season`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- The league's once-a-season guard: opening it, and the closing recap. No column for the OGS league -- there is one,
-- it is permanent, and it lives in `ogs.league.id` / `ogs.league.auth` in config.properties.
CREATE TABLE `league_seasons` (
  `season` VARCHAR(9) NOT NULL,
  `opened` DATETIME NULL,
  `closed` DATETIME NULL,
  PRIMARY KEY (`season`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- One row per session, created by its draw -- so a session not yet drawn has no row at all. The three columns are
-- three once-only guards: the draw, its announcement, and the settlement. The service ticks every ten minutes, so
-- the start of a session is seen about 1400 times and it is `drawn`, not the calendar, that says it is done.
CREATE TABLE `league_sessions` (
  `season` VARCHAR(9) NOT NULL,
  `session` INT(11) NOT NULL,
  `drawn` DATETIME NULL,
  `notified` DATETIME NULL,
  `settled` DATETIME NULL,
  PRIMARY KEY (`season`, `session`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- The OGS side, for life and with no season: a member registered with OGS stays registered, including after leaving
-- their academy. There is deliberately no `ogs_member_id` -- it is sha256(discord_id + league.member.salt), computed
-- by one function and never stored -- and no rating, which is the same constant for everybody.
-- `ogs_registered IS NULL` is the work queue of the tick that calls `PUT member/{id}`.
CREATE TABLE `league_players` (
  `discord_id` VARCHAR(255) NOT NULL,
  `ogs_registered` DATETIME NULL,
  PRIMARY KEY (`discord_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 ROW_FORMAT = DYNAMIC;

-- The academy, per season. The season in the PK is what empties the academies for free on 1 September: the new season
-- simply has no rows, and the old ones stay readable. `active` rather than a delete, so a player who leaves keeps
-- their matches and can come back. The house is not here -- `house_members` is its source, and it is frozen on the
-- match instead, where it matters.
CREATE TABLE `league_members` (
  `season` VARCHAR(9) NOT NULL,
  `discord_id` VARCHAR(255) NOT NULL,
  `joined` DATETIME NOT NULL,
  `active` TINYINT(1) NOT NULL DEFAULT 1,
  `left_since` DATETIME NULL,
  PRIMARY KEY (`season`, `discord_id`),
  KEY `league_members_active` (`season`, `active`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 ROW_FORMAT = DYNAMIC;

-- The pairings, and the only source of the standings -- there is no league points table, the renown is derived from
-- this one. The PK plus the unique key on white state the domain rule: at most one match per player per session,
-- whichever side they are on, which is what makes a draw run twice harmless. The two house ids are frozen at draw
-- time so an academy's total never shrinks. `league_match_id` is what OGS keys its idempotence on, prefixed with
-- db.name because dev and prod share the one OGS league. `ogs_match_id` is indexed because the callback arrives on it.
-- `result` is NULL while open, the winner OGS names once played, and 'unplayed' -- terminal -- once the session is
-- settled without a result. The settlement leaves no match at NULL, which is what stops a match pending forever from
-- silently costing both players the perfect-attendance bonus.
-- No foreign key on `gold_id`: CleanService deletes games after 32 days and a November match must stay readable in
-- May, which is also why `result` is copied here rather than joined.
-- ROW_FORMAT is explicit because that PK is 1060 bytes in utf8mb4 -- fine under DYNAMIC, too wide for COMPACT.
CREATE TABLE `league_matches` (
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

-- The players a draw left without an opponent, written by the draw and never by the settlement. It is the only thing
-- that makes the perfect-attendance bonus computable: without it, a session with no match is indistinguishable from
-- a session where the player was not there. `reason` is 'ODD' or 'NO_RIVAL' and no code reads it -- it answers "why
-- was I not drawn?" months later.
CREATE TABLE `league_exemptions` (
  `season` VARCHAR(9) NOT NULL,
  `session` INT(11) NOT NULL,
  `discord_id` VARCHAR(255) NOT NULL,
  `reason` VARCHAR(32) NOT NULL,
  `created` DATETIME NOT NULL,
  PRIMARY KEY (`season`, `session`, `discord_id`),
  KEY `league_exemptions_player` (`season`, `discord_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 ROW_FORMAT = DYNAMIC;

-- ---------------------------------------------------------------------------------------------------------------
-- Reference data
-- ---------------------------------------------------------------------------------------------------------------

INSERT INTO `gold_tiers` (`rank`, `name`, `min`, `max`) VALUES
  (1, 'Novice', 0, 1000),
  (2, 'Initié', 1000, 1200),
  (3, 'Adepte', 1200, 1400),
  (4, 'Elite', 1400, 1600),
  (5, 'Maître', 1600, 1800),
  (6, 'Grand-Maître', 1800, 2000),
  (7, 'Immortel', 2000, 2200),
  (8, 'Légendaire', 2200, 3000);

-- `houses` holds four rows, listed here by the two columns anything else keys on. Their taglines, colours and RP
-- paragraphs are NOT duplicated here on purpose: `migration maisons.sql` is the single authority for that text,
-- and a second copy would drift the first time someone rewrites a paragraph.
--
--   1  FILS_DU_FROID       Fils du Froid
--   2  NEXUS_ALPHA         Nexus Alpha
--   3  SABRE_SILENCIEUX    Sabre Silencieux
--   4  LUNAIRES_AETHER     Lunaires d’Æther

-- ---------------------------------------------------------------------------------------------------------------
-- Views
--
-- The live ones carry ALGORITHM = UNDEFINED and SQL SECURITY DEFINER, owned by the DB user the app connects with.
-- Recreate them as that user so the definer does not move.
-- ---------------------------------------------------------------------------------------------------------------

-- What GoldService averages into a rating. `error` is the SUM of the per-platform error flags, so a single broken
-- scraper nulls the whole rating -- which is deliberate, but worth remembering when a platform starts failing.
CREATE OR REPLACE ALGORITHM = UNDEFINED SQL SECURITY DEFINER VIEW `gold_ranks` AS
  SELECT `discord`.`discord_id`, `kgs_rank`, `kgs_rank_date`, `ogs_rank`,
  (IF(kgs.error IS NULL, 0, kgs.error) + IF(ogs.error IS NULL, 0, ogs.error)) AS error
  FROM `discord_user_info` AS `discord`
  LEFT JOIN `kgs_user_info` AS `kgs` ON `discord`.`discord_id` = `kgs`.`discord_id`
  LEFT JOIN `ogs_user_info` AS `ogs` ON `discord`.`discord_id` = `ogs`.`discord_id`;

-- Everything the website shows about a player. LEFT JOINs throughout, so a platform's columns are all null exactly
-- when the player has no account there, which is how ApiDbPlayer decides what to put in `accounts`.
CREATE OR REPLACE ALGORITHM = UNDEFINED SQL SECURITY DEFINER VIEW `api_players` AS
  SELECT
  `discord`.`discord_id`, `discord`.`discord_name`, `discord`.`discord_avatar`,
  `kgs_id`, `kgs_rank`,
  `ogs_id`, `ogs_name`, `ogs_rank`,
  `fox_id`, `fox_name`, `fox_rank`,
  `gold`.`rating`, `gold`.`tier_rank`, `tier`.`name` AS `tier_name`,
  `fgc`.`total_ranked_games`, `fgc`.`gold_ranked_games`
  FROM `discord_user_info` AS `discord`
  LEFT JOIN `gold_ratings` AS `gold` ON `gold`.`discord_id` = `discord`.`discord_id`
  LEFT JOIN `gold_tiers` AS `tier` ON `gold`.`tier_rank` = `tier`.`rank`
  LEFT JOIN `fgc_validity` AS `fgc` ON `fgc`.`discord_id` = `discord`.`discord_id`
  LEFT JOIN `kgs_user_info` AS `kgs` ON `discord`.`discord_id` = `kgs`.`discord_id`
  LEFT JOIN `ogs_user_info` AS `ogs` ON `discord`.`discord_id` = `ogs`.`discord_id`
  LEFT JOIN `fox_user_info` AS `fox` ON `discord`.`discord_id` = `fox`.`discord_id`;

-- Games between two known players, one branch per platform that stores games. INNER JOINs, so a game against
-- someone outside the community never shows up, and the `result` filter keeps unfinished games out of the API.
CREATE OR REPLACE ALGORITHM = UNDEFINED SQL SECURITY DEFINER VIEW `api_games` AS
  SELECT `game`.`gold_id`, `game`.`date`, `game`.`result`, `game`.`sgf`,
  `black_discord`.`discord_id` AS `black_discord_id`,
  `black_discord`.`discord_name` AS `black_discord_name`,
  `black_discord`.`discord_avatar` AS `black_discord_avatar`,
  `black_gold`.`rating` AS `black_rating`,
  `black_gold`.`tier_rank` AS `black_tier_rank`,
  `black_tier`.`name` AS `black_tier_name`,
  `white_discord`.`discord_id` AS `white_discord_id`,
  `white_discord`.`discord_name` AS `white_discord_name`,
  `white_discord`.`discord_avatar` AS `white_discord_avatar`,
  `white_gold`.`rating` AS `white_rating`,
  `white_gold`.`tier_rank` AS `white_tier_rank`,
  `white_tier`.`name` AS `white_tier_name`
  FROM `ogs_games` AS `game`
  INNER JOIN `ogs_user_info` AS `black_ogs` ON `game`.`black_id` = `black_ogs`.`ogs_id`
  INNER JOIN `discord_user_info` AS `black_discord` ON `black_ogs`.`discord_id` = `black_discord`.`discord_id`
  INNER JOIN `gold_ratings` AS `black_gold` ON `black_discord`.`discord_id` = `black_gold`.`discord_id`
  INNER JOIN `gold_tiers` AS `black_tier` ON `black_gold`.`tier_rank` = `black_tier`.`rank`
  INNER JOIN `ogs_user_info` AS `white_ogs` ON `game`.`white_id` = `white_ogs`.`ogs_id`
  INNER JOIN `discord_user_info` AS `white_discord` ON `white_ogs`.`discord_id` = `white_discord`.`discord_id`
  INNER JOIN `gold_ratings` AS `white_gold` ON `white_discord`.`discord_id` = `white_gold`.`discord_id`
  INNER JOIN `gold_tiers` AS `white_tier` ON `white_gold`.`tier_rank` = `white_tier`.`rank`
  WHERE `game`.`result` != 'unfinished'
  UNION
  SELECT `game`.`gold_id`, `game`.`date`, `game`.`result`, `game`.`sgf`,
  `black_discord`.`discord_id` AS `black_discord_id`,
  `black_discord`.`discord_name` AS `black_discord_name`,
  `black_discord`.`discord_avatar` AS `black_discord_avatar`,
  `black_gold`.`rating` AS `black_rating`,
  `black_gold`.`tier_rank` AS `black_tier_rank`,
  `black_tier`.`name` AS `black_tier_name`,
  `white_discord`.`discord_id` AS `white_discord_id`,
  `white_discord`.`discord_name` AS `white_discord_name`,
  `white_discord`.`discord_avatar` AS `white_discord_avatar`,
  `white_gold`.`rating` AS `white_rating`,
  `white_gold`.`tier_rank` AS `white_tier_rank`,
  `white_tier`.`name` AS `white_tier_name`
  FROM `kgs_games` AS `game`
  INNER JOIN `kgs_user_info` AS `black_kgs` ON `game`.`black_id` = `black_kgs`.`kgs_id`
  INNER JOIN `discord_user_info` AS `black_discord` ON `black_kgs`.`discord_id` = `black_discord`.`discord_id`
  INNER JOIN `gold_ratings` AS `black_gold` ON `black_discord`.`discord_id` = `black_gold`.`discord_id`
  INNER JOIN `gold_tiers` AS `black_tier` ON `black_gold`.`tier_rank` = `black_tier`.`rank`
  INNER JOIN `kgs_user_info` AS `white_kgs` ON `game`.`white_id` = `white_kgs`.`kgs_id`
  INNER JOIN `discord_user_info` AS `white_discord` ON `white_kgs`.`discord_id` = `white_discord`.`discord_id`
  INNER JOIN `gold_ratings` AS `white_gold` ON `white_discord`.`discord_id` = `white_gold`.`discord_id`
  INNER JOIN `gold_tiers` AS `white_tier` ON `white_gold`.`tier_rank` = `white_tier`.`rank`
  WHERE `game`.`result` != 'unfinished';

-- Games that count towards FGC validity: 19x19, no handicap, standard komi, finished, within 30 days. LEFT JOINs
-- here, unlike api_games, because a game against an unknown opponent still counts as a game played.
CREATE OR REPLACE ALGORITHM = UNDEFINED SQL SECURITY DEFINER VIEW `fgc_validity_games` AS
  SELECT `game`.`gold_id`,
  `black`.`discord_id` AS `black_discord_id`, `white`.`discord_id` AS `white_discord_id`,
  `game`.`ranked`
  FROM `ogs_games` AS `game`
  LEFT JOIN `ogs_user_info` AS `black` ON `game`.`black_id` = `black`.`ogs_id`
  LEFT JOIN `ogs_user_info` AS `white` ON `game`.`white_id` = `white`.`ogs_id`
  WHERE DATEDIFF(NOW(), `game`.`date`) <= 30
    AND `game`.`size` = 19
    AND `game`.`handicap` = 0
    AND `game`.`result` != 'unfinished'
    AND `game`.`komi` > 6 AND `game`.`komi` < 9
  UNION
  SELECT `game`.`gold_id`,
  `black`.`discord_id` AS `black_discord_id`, `white`.`discord_id` AS `white_discord_id`,
  `game`.`ranked`
  FROM `kgs_games` AS `game`
  LEFT JOIN `kgs_user_info` AS `black` ON `game`.`black_id` = `black`.`kgs_id`
  LEFT JOIN `kgs_user_info` AS `white` ON `game`.`white_id` = `white`.`kgs_id`
  WHERE DATEDIFF(NOW(), `game`.`date`) <= 30
    AND `game`.`size` = 19
    AND `game`.`handicap` = 0
    AND `game`.`result` != 'unfinished'
    AND `game`.`komi` > 6 AND `game`.`komi` < 9;

-- Every finished game either platform stores, flattened to the two Discord ids, for the house scanner to score.
-- Same union as fgc_validity_games with none of its filters -- house scoring takes any size, handicap and komi.
-- The LEFT JOINs are load-bearing: an opponent unknown to the server comes back with a NULL discord_id, and that
-- null is what tells the `gold_opponent` bonus apart from no bonus. `handicap` is here for the "even game" bonus,
-- which is handicap = 0 and not a drawn result.
CREATE OR REPLACE ALGORITHM = UNDEFINED SQL SECURITY DEFINER VIEW `house_games` AS
  SELECT `game`.`gold_id`, `game`.`date`, `game`.`result`,
  `game`.`ranked`, `game`.`long_game`, `game`.`handicap`,
  `black`.`discord_id` AS `black_discord_id`, `white`.`discord_id` AS `white_discord_id`
  FROM `ogs_games` AS `game`
  LEFT JOIN `ogs_user_info` AS `black` ON `game`.`black_id` = `black`.`ogs_id`
  LEFT JOIN `ogs_user_info` AS `white` ON `game`.`white_id` = `white`.`ogs_id`
  WHERE `game`.`result` != 'unfinished'
  UNION
  SELECT `game`.`gold_id`, `game`.`date`, `game`.`result`,
  `game`.`ranked`, `game`.`long_game`, `game`.`handicap`,
  `black`.`discord_id` AS `black_discord_id`, `white`.`discord_id` AS `white_discord_id`
  FROM `kgs_games` AS `game`
  LEFT JOIN `kgs_user_info` AS `black` ON `game`.`black_id` = `black`.`kgs_id`
  LEFT JOIN `kgs_user_info` AS `white` ON `game`.`white_id` = `white`.`kgs_id`
  WHERE `game`.`result` != 'unfinished';
