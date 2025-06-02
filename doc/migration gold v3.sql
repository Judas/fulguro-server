-- DISCORD
DROP TABLE IF EXISTS `discord_user_info`;
CREATE TABLE `discord_user_info` (
  `discord_id` VARCHAR(255) NOT NULL,
  `discord_name` VARCHAR(255) NOT NULL,
  `discord_avatar` VARCHAR(255) NOT NULL,
  `updated` DATETIME NULL,
  `error` DATETIME NULL,
  PRIMARY KEY (`discord_id`)
)
ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8mb4;

INSERT INTO `discord_user_info`
  SELECT u.discord_id, u.name AS `discord_name`, u.avatar AS `discord_avatar`, NULL AS `updated`, NULL AS `error`
  FROM `users` AS u
  WHERE u.name IS NOT NULL AND u.avatar IS NOT NULL;

-- KGS

DROP TABLE IF EXISTS `kgs_user_info`;
CREATE TABLE `kgs_user_info` (
  `discord_id` VARCHAR(255) NOT NULL,
  `kgs_id` VARCHAR(255) NOT NULL,
  `kgs_rank` VARCHAR(255) NOT NULL,
  `updated` DATETIME NULL,
  `error` DATETIME NULL,
  PRIMARY KEY (`discord_id`)
)
ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8mb4;

INSERT INTO `kgs_user_info`
  SELECT u.discord_id, u.kgs_id, "?" AS `kgs_rank`, NULL AS `updated`, NULL AS `error`
  FROM `users` AS u
  WHERE `kgs_id` IS NOT NULL;

DROP TABLE IF EXISTS `kgs_games`;
CREATE TABLE `kgs_games` (
  `date` DATETIME NOT NULL,
  `black_name` VARCHAR(255) NOT NULL,
  `black_rank` VARCHAR(255) NOT NULL,
  `white_name` VARCHAR(255) NOT NULL,
  `white_rank` VARCHAR(255) NOT NULL,
  `size` INT(11) NOT NULL,
  `komi` DOUBLE NOT NULL,
  `handicap` INT(11) NOT NULL,
  `long_game` TINYINT(1) NOT NULL,
  `result` VARCHAR(255) NOT NULL,
  `sgf` TEXT NOT NULL,
  PRIMARY KEY (`date`, `black_name`, `white_name`)
)
ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8mb4;
