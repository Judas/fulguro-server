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

-- IGS

DROP TABLE IF EXISTS `igs_user_info`;
CREATE TABLE `igs_user_info` (
  `discord_id` VARCHAR(255) NOT NULL,
  `igs_id` VARCHAR(255) NOT NULL,
  `igs_rank` VARCHAR(255) NOT NULL,
  `updated` DATETIME NULL,
  `error` DATETIME NULL,
  PRIMARY KEY (`discord_id`)
)
ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8mb4;

INSERT INTO `igs_user_info`
  SELECT u.discord_id, u.igs_id, "?" AS `igs_rank`, NULL AS `updated`, NULL AS `error`
  FROM `users` AS u
  WHERE `igs_id` IS NOT NULL;

-- FFG

DROP TABLE IF EXISTS `ffg_user_info`;
CREATE TABLE `ffg_user_info` (
  `discord_id` VARCHAR(255) NOT NULL,
  `ffg_id` VARCHAR(255) NOT NULL,
  `ffg_name` VARCHAR(255) NOT NULL,
  `ffg_rank` VARCHAR(255) NOT NULL,
  `updated` DATETIME NULL,
  `error` DATETIME NULL,
  PRIMARY KEY (`discord_id`)
)
ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8mb4;

INSERT INTO `ffg_user_info`
  SELECT u.discord_id, u.ffg_id, "" AS `ffg_name`, "" AS `ffg_rank`, NULL AS `updated`, NULL AS `error`
  FROM `users` AS u
  WHERE `ffg_id` IS NOT NULL;

-- EGF

DROP TABLE IF EXISTS `egf_user_info`;
CREATE TABLE `egf_user_info` (
  `discord_id` VARCHAR(255) NOT NULL,
  `egf_id` VARCHAR(255) NOT NULL,
  `egf_name` VARCHAR(255) NOT NULL,
  `egf_rank` VARCHAR(255) NOT NULL,
  `updated` DATETIME NULL,
  `error` DATETIME NULL,
  PRIMARY KEY (`discord_id`)
)
ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8mb4;

INSERT INTO `egf_user_info`
  SELECT u.discord_id, u.egf_id, "" AS `egf_name`, "" AS `egf_rank`, NULL AS `updated`, NULL AS `error`
  FROM `users` AS u
  WHERE `egf_id` IS NOT NULL;
