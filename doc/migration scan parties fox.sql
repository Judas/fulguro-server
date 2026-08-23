-- Store recent FOX games for FGC validity and house points. Apply before deploying the scanner jar.
-- Re-runnable: every added column is guarded and the table/views are idempotent.
--
--   mysql -u <db.user> -p fg_dev  < "doc/migration scan parties fox.sql"
--   mysql -u <db.user> -p fg_prod < "doc/migration scan parties fox.sql"

SET @add_fox_total_win := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `fox_user_info` ADD COLUMN `total_win` INT(11) NULL AFTER `fox_rank`', 'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'fox_user_info' AND COLUMN_NAME = 'total_win'
);
PREPARE add_fox_total_win FROM @add_fox_total_win;
EXECUTE add_fox_total_win;
DEALLOCATE PREPARE add_fox_total_win;

SET @add_fox_total_lost := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `fox_user_info` ADD COLUMN `total_lost` INT(11) NULL AFTER `total_win`', 'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'fox_user_info' AND COLUMN_NAME = 'total_lost'
);
PREPARE add_fox_total_lost FROM @add_fox_total_lost;
EXECUTE add_fox_total_lost;
DEALLOCATE PREPARE add_fox_total_lost;

SET @add_fox_total_equal := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `fox_user_info` ADD COLUMN `total_equal` INT(11) NULL AFTER `total_lost`', 'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'fox_user_info' AND COLUMN_NAME = 'total_equal'
);
PREPARE add_fox_total_equal FROM @add_fox_total_equal;
EXECUTE add_fox_total_equal;
DEALLOCATE PREPARE add_fox_total_equal;

SET @add_fox_updated := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `fox_user_info` ADD COLUMN `updated` DATETIME NULL AFTER `total_equal`', 'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'fox_user_info' AND COLUMN_NAME = 'updated'
);
PREPARE add_fox_updated FROM @add_fox_updated;
EXECUTE add_fox_updated;
DEALLOCATE PREPARE add_fox_updated;

SET @add_fox_error := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `fox_user_info` ADD COLUMN `error` TINYINT(1) NOT NULL DEFAULT 0 AFTER `updated`', 'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'fox_user_info' AND COLUMN_NAME = 'error'
);
PREPARE add_fox_error FROM @add_fox_error;
EXECUTE add_fox_error;
DEALLOCATE PREPARE add_fox_error;

CREATE TABLE IF NOT EXISTS `fox_games` (
  `gold_id` VARCHAR(255) NOT NULL,
  `chess_id` VARCHAR(255) NOT NULL,
  `date` DATETIME NOT NULL,
  `black_id` VARCHAR(255) NOT NULL,
  `black_name` VARCHAR(255) NOT NULL,
  `black_rank` VARCHAR(255) NOT NULL,
  `white_id` VARCHAR(255) NOT NULL,
  `white_name` VARCHAR(255) NOT NULL,
  `white_rank` VARCHAR(255) NOT NULL,
  `size` INT(11) NOT NULL,
  `komi` DOUBLE NOT NULL,
  `handicap` INT(11) NOT NULL,
  `ranked` TINYINT(1) NOT NULL DEFAULT 0,
  `long_game` TINYINT(1) NOT NULL DEFAULT 0,
  `result` VARCHAR(255) NOT NULL,
  `sgf` TEXT NOT NULL,
  PRIMARY KEY (`gold_id`),
  UNIQUE KEY `fox_games_chess_id_uq` (`chess_id`),
  KEY `fox_games_date_idx` (`date`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- FOX SGFs encode komi in fiftieths of a point (375 means 7.5). This condition makes the data fix re-runnable.
UPDATE `fox_games` SET `komi` = `komi` / 50 WHERE `komi` >= 50;

CREATE OR REPLACE ALGORITHM = UNDEFINED SQL SECURITY DEFINER VIEW `fgc_validity_games` AS
  SELECT game.gold_id, black.discord_id AS black_discord_id, white.discord_id AS white_discord_id, game.ranked
  FROM ogs_games AS game
  LEFT JOIN ogs_user_info AS black ON game.black_id = black.ogs_id
  LEFT JOIN ogs_user_info AS white ON game.white_id = white.ogs_id
  WHERE DATEDIFF(NOW(), game.date) <= 30 AND game.size = 19 AND game.handicap = 0
    AND game.result != 'unfinished' AND game.komi > 6 AND game.komi < 9
  UNION
  SELECT game.gold_id, black.discord_id, white.discord_id, game.ranked
  FROM kgs_games AS game
  LEFT JOIN kgs_user_info AS black ON game.black_id = black.kgs_id
  LEFT JOIN kgs_user_info AS white ON game.white_id = white.kgs_id
  WHERE DATEDIFF(NOW(), game.date) <= 30 AND game.size = 19 AND game.handicap = 0
    AND game.result != 'unfinished' AND game.komi > 6 AND game.komi < 9
  UNION
  SELECT game.gold_id, black.discord_id, white.discord_id, 1 AS ranked
  FROM fox_games AS game
  LEFT JOIN fox_user_info AS black ON game.black_id = black.fox_id
  LEFT JOIN fox_user_info AS white ON game.white_id = white.fox_id
  WHERE DATEDIFF(NOW(), game.date) <= 30 AND game.size = 19 AND game.handicap = 0
    AND game.result != 'unfinished' AND game.komi > 6 AND game.komi < 9;

CREATE OR REPLACE ALGORITHM = UNDEFINED SQL SECURITY DEFINER VIEW `house_games` AS
  SELECT game.gold_id, game.date, game.result, game.ranked, game.long_game, game.handicap,
    black.discord_id AS black_discord_id, white.discord_id AS white_discord_id
  FROM ogs_games AS game
  LEFT JOIN ogs_user_info AS black ON game.black_id = black.ogs_id
  LEFT JOIN ogs_user_info AS white ON game.white_id = white.ogs_id
  WHERE game.result != 'unfinished'
  UNION
  SELECT game.gold_id, game.date, game.result, game.ranked, game.long_game, game.handicap,
    black.discord_id, white.discord_id
  FROM kgs_games AS game
  LEFT JOIN kgs_user_info AS black ON game.black_id = black.kgs_id
  LEFT JOIN kgs_user_info AS white ON game.white_id = white.kgs_id
  WHERE game.result != 'unfinished'
  UNION
  SELECT game.gold_id, game.date, game.result, game.ranked, game.long_game, game.handicap,
    black.discord_id, white.discord_id
  FROM fox_games AS game
  LEFT JOIN fox_user_info AS black ON game.black_id = black.fox_id
  LEFT JOIN fox_user_info AS white ON game.white_id = white.fox_id
  WHERE game.result != 'unfinished';

-- Verification:
--   SHOW COLUMNS FROM fox_user_info;
--   SHOW COLUMNS FROM fox_games;
--   SELECT COUNT(*) FROM fgc_validity_games WHERE gold_id LIKE 'FOX\_%';
--   SELECT COUNT(*) FROM house_games WHERE gold_id LIKE 'FOX\_%';
