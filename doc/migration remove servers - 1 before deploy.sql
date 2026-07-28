-- Removal of the FOX and IGS servers and of the FFG and EGF federations, part 1 of 2.
-- Only KGS and OGS remain.
--
-- APPLY THIS BEFORE DEPLOYING, with the old jar still running, then deploy, then apply part 2.
--
--   * everything here is either additive or a view swap, and the old jar tolerates both: it reads the columns that
--     disappear as null, which is what the new jar computes anyway, and it never writes to a view;
--   * kgs_rank_date has to exist before the new jar starts, because its KgsService writes that column on every
--     refresh and would otherwise fail on all 105 KGS users;
--   * the four platform tables are still here, so the old FoxService, IgsService, FfgService and EgfService keep
--     working until they are gone with the jar. Dropping them early is what part 2 is for -- do it under the old jar
--     and every one of those services fails on every tick, which turns GET /gold/api/health into a lasting 503.
--
-- Run it as the DB user that owns the existing views (db.user in config.properties). The views below deliberately
-- carry no DEFINER clause so that the definer stays whoever runs this; naming another one would need SUPER on
-- MySQL 5.7 anyway.
--
--   mysql -u <db.user> -p fg_prod < "doc/migration remove servers - 1 before deploy.sql"
--
-- Every statement is idempotent, so the file can be re-run.
--
-- Expect the ladder to move once this lands: 18 players are stuck with gold_ratings.error = 1 only because the FOX
-- or EGF scraper fails for them (all 13 fox_user_info rows and all 14 egf_user_info rows have error = 1), and
-- dropping those joins un-blocks them. GoldService recomputes one row per 15s, so over the ~75 min of a full sweep
-- expect 3 ":tada: Promotion Gold" messages and 4 silent demotions. Nobody loses their rating.

-- ---------------------------------------------------------------------------------------------------------------
-- kgs_user_info.kgs_rank_date
--
-- How old the stored kgs_rank is: KGS publishes no current rank, only the one a player held in each archived game,
-- and this community plays there rarely, so the rank can be years old. UserRanks.computeRating fades the KGS weight
-- by this date. NULL means unknown, and counts as current until the next refresh dates it.
--
-- Guarded by hand because MySQL 5.7 has no ADD COLUMN IF NOT EXISTS.
-- ---------------------------------------------------------------------------------------------------------------

SET @column_exists = (
  SELECT COUNT(*) FROM `information_schema`.`COLUMNS`
  WHERE `TABLE_SCHEMA` = DATABASE()
    AND `TABLE_NAME` = 'kgs_user_info'
    AND `COLUMN_NAME` = 'kgs_rank_date'
);
SET @statement = IF(
  @column_exists = 0,
  'ALTER TABLE `kgs_user_info` ADD COLUMN `kgs_rank_date` DATETIME NULL AFTER `kgs_rank`',
  'DO 0'
);
PREPARE add_column FROM @statement;
EXECUTE add_column;
DEALLOCATE PREPARE add_column;

-- ---------------------------------------------------------------------------------------------------------------
-- Views
--
-- CREATE OR REPLACE rather than DROP + CREATE: the replace is an atomic metadata swap, so no API request can land in
-- a window where api_players or api_games does not exist.
--
-- fgc_validity_games needs no change here: it already unions OGS and KGS only.
-- ---------------------------------------------------------------------------------------------------------------

CREATE OR REPLACE ALGORITHM = UNDEFINED SQL SECURITY DEFINER VIEW `gold_ranks` AS
  SELECT `discord`.`discord_id`, `kgs_rank`, `kgs_rank_date`, `ogs_rank`,
  (IF(kgs.error IS NULL, 0, kgs.error) + IF(ogs.error IS NULL, 0, ogs.error)) AS error
  FROM `discord_user_info` AS `discord`
  LEFT JOIN `kgs_user_info` AS `kgs` ON `discord`.`discord_id` = `kgs`.`discord_id`
  LEFT JOIN `ogs_user_info` AS `ogs` ON `discord`.`discord_id` = `ogs`.`discord_id`;

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

-- Only the OGS and KGS branches are left. The FOX one never returned a row anyway: fox_games is empty and every
-- fox_user_info.fox_id is -1, so its INNER JOIN never matched.
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

-- Checks. gold_ranks and api_players should both hold one row per discord_user_info row (212 at the time of
-- writing), and api_games exactly as many rows as before -- run the same count before applying to compare.
--
--   SELECT COUNT(*) FROM `gold_ranks`;
--   SELECT COUNT(*) FROM `api_players`;
--   SELECT COUNT(*) FROM `api_games`;
--   SHOW COLUMNS FROM `kgs_user_info` LIKE 'kgs_rank_date';
