-- Add account-only FOX support. Apply before deploying the jar that exposes FOX from /gold/api/accounts.
-- The old jar ignores the extra api_players columns, so the migration is safe to run first.
--
-- Run as the DB user that owns api_players:
--   mysql -u <db.user> -p fg_dev  < "doc/migration ajout compte fox.sql"
--   mysql -u <db.user> -p fg_prod < "doc/migration ajout compte fox.sql"

CREATE TABLE IF NOT EXISTS `fox_user_info` (
  `discord_id` VARCHAR(255) NOT NULL,
  `fox_id` VARCHAR(255) NOT NULL,
  `fox_name` VARCHAR(255) NOT NULL,
  `fox_rank` VARCHAR(255) NOT NULL,
  PRIMARY KEY (`discord_id`),
  UNIQUE KEY `fox_user_info_fox_id_uq` (`fox_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

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

-- Checks: one row per Discord user, FOX absent from the rating and game views.
--   SELECT COUNT(*) FROM `api_players`;
--   SHOW COLUMNS FROM `fox_user_info`;
--   SELECT * FROM `api_players` WHERE `fox_id` IS NOT NULL;
