-- Reference schema of the fg_prod database: 9 tables, 4 views, and the gold_tiers reference rows.
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
--
-- Only KGS and OGS are aggregated. FOX, IGS, FFG and EGF were removed in 8.8 -- see
-- `migration remove servers - 1 before deploy.sql` and `- 2 after deploy.sql` for the change that got us here.

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
  `gold`.`rating`, `gold`.`tier_rank`, `tier`.`name` AS `tier_name`,
  `fgc`.`total_ranked_games`, `fgc`.`gold_ranked_games`
  FROM `discord_user_info` AS `discord`
  LEFT JOIN `gold_ratings` AS `gold` ON `gold`.`discord_id` = `discord`.`discord_id`
  LEFT JOIN `gold_tiers` AS `tier` ON `gold`.`tier_rank` = `tier`.`rank`
  LEFT JOIN `fgc_validity` AS `fgc` ON `fgc`.`discord_id` = `discord`.`discord_id`
  LEFT JOIN `kgs_user_info` AS `kgs` ON `discord`.`discord_id` = `kgs`.`discord_id`
  LEFT JOIN `ogs_user_info` AS `ogs` ON `discord`.`discord_id` = `ogs`.`discord_id`;

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
