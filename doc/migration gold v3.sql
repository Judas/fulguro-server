-- DISCORD

DROP TABLE IF EXISTS `discord_user_info`;
CREATE TABLE `discord_user_info` (
  `discord_id` VARCHAR(255) NOT NULL,
  `discord_name` VARCHAR(255) NOT NULL,
  `discord_avatar` VARCHAR(255) NOT NULL,
  `updated` DATETIME NULL,
  `error` TINYINT(1) NOT NULL,
  PRIMARY KEY (`discord_id`)
)
ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8mb4;

INSERT INTO `discord_user_info`
  SELECT u.discord_id, u.name AS `discord_name`, u.avatar AS `discord_avatar`, NULL AS `updated`, 0 AS `error`
  FROM `users` AS u
  WHERE u.name IS NOT NULL AND u.avatar IS NOT NULL;

-- KGS

DROP TABLE IF EXISTS `kgs_user_info`;
CREATE TABLE `kgs_user_info` (
  `discord_id` VARCHAR(255) NOT NULL,
  `kgs_id` VARCHAR(255) NOT NULL,
  `kgs_rank` VARCHAR(255) NOT NULL,
  `updated` DATETIME NULL,
  `error`  TINYINT(1) NOT NULL,
  PRIMARY KEY (`discord_id`)
)
ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8mb4;

INSERT INTO `kgs_user_info`
  SELECT u.discord_id, u.kgs_id, "?" AS `kgs_rank`, NULL AS `updated`, 0 AS `error`
  FROM `users` AS u
  WHERE `kgs_id` IS NOT NULL;

DROP TABLE IF EXISTS `kgs_games`;
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
)
ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8mb4;

-- OGS

DROP TABLE IF EXISTS `ogs_user_info`;
CREATE TABLE `ogs_user_info` (
  `discord_id` VARCHAR(255) NOT NULL,
  `ogs_id` INT(11) NOT NULL,
  `ogs_name` VARCHAR(255) NOT NULL,
  `ogs_rank` VARCHAR(255) NOT NULL,
  `updated` DATETIME NULL,
  `error` TINYINT(1) NOT NULL,
  PRIMARY KEY (`discord_id`)
)
ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8mb4;

INSERT INTO `ogs_user_info`
  SELECT u.discord_id, u.ogs_id, "" AS `ogs_name`, "?" AS `ogs_rank`, NULL AS `updated`, 0 AS `error`
  FROM `users` AS u
  WHERE `ogs_id` IS NOT NULL;

DROP TABLE IF EXISTS `ogs_games`;
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
)
ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8mb4;

-- FOX

DROP TABLE IF EXISTS `fox_user_info`;
CREATE TABLE `fox_user_info` (
  `discord_id` VARCHAR(255) NOT NULL,
  `fox_id` INT(11) NOT NULL,
  `fox_name` VARCHAR(255) NOT NULL,
  `fox_rank` VARCHAR(255) NOT NULL,
  `updated` DATETIME NULL,
  `error` TINYINT(1) NOT NULL,
  PRIMARY KEY (`discord_id`)
)
ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8mb4;

INSERT INTO `fox_user_info`
  SELECT u.discord_id, -1 AS `fox_id`, u.fox_pseudo AS `fox_name`, "?" AS `fox_rank`, NULL AS `updated`, 0 AS `error`
  FROM `users` AS u
  WHERE `fox_pseudo` IS NOT NULL;

DROP TABLE IF EXISTS `fox_games`;
CREATE TABLE `fox_games` (
  `gold_id` VARCHAR(255) NOT NULL,
  `id` BIGINT(25) NOT NULL,
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
  `error` TINYINT(1) NOT NULL,
  PRIMARY KEY (`discord_id`)
)
ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8mb4;

INSERT INTO `igs_user_info`
  SELECT u.discord_id, u.igs_id, "?" AS `igs_rank`, NULL AS `updated`, 0 AS `error`
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
  `error` TINYINT(1) NOT NULL,
  PRIMARY KEY (`discord_id`)
)
ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8mb4;

INSERT INTO `ffg_user_info`
  SELECT u.discord_id, u.ffg_id, "" AS `ffg_name`, "" AS `ffg_rank`, NULL AS `updated`, 0 AS `error`
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
  `error` TINYINT(1) NOT NULL,
  PRIMARY KEY (`discord_id`)
)
ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8mb4;

INSERT INTO `egf_user_info`
  SELECT u.discord_id, u.egf_id, "" AS `egf_name`, "" AS `egf_rank`, NULL AS `updated`, 0 AS `error`
  FROM `users` AS u
  WHERE `egf_id` IS NOT NULL;

-- GOLD

DROP TABLE IF EXISTS `gold_tiers`;
CREATE TABLE `gold_tiers` (
  `rank` INT(11) NOT NULL,
  `name` varchar(255) NOT NULL,
  `min`INT(11) NOT NULL,
  `max` INT(11) NOT NULL,
  PRIMARY KEY (`rank`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO `gold_tiers`(`rank`,`name`,`min`, `max`)
VALUES
('1','Novice','0','1000'),
('2','Initié','1000','1200'),
('3','Adepte','1200','1400'),
('4','Elite','1400','1600'),
('5','Maître','1600','1800'),
('6','Grand-Maître','1800','2000'),
('7','Immortel','2000','2200'),
('8','Légendaire','2200','3000');

DROP TABLE IF EXISTS `gold_ratings`;
CREATE TABLE `gold_ratings` (
  `discord_id` VARCHAR(255) NOT NULL,
  `rating` DOUBLE NOT NULL,
  `tier_rank` INT(11) NOT NULL,
  `updated` DATETIME NULL,
  `error` TINYINT(1) NOT NULL,
  PRIMARY KEY (`discord_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO `gold_ratings`
  SELECT u.discord_id, 0 AS `rating`, 0 AS `tier_rank`, NULL AS `updated`, 0 AS `error`
  FROM `users` AS u
  WHERE `discord_id` IS NOT NULL;

DROP VIEW IF EXISTS `gold_ranks`;
CREATE VIEW `gold_ranks` AS
  SELECT discord.discord_id, kgs_rank, ogs_rank, fox_rank, igs_rank, ffg_rank, egf_rank
  FROM discord_user_info AS discord
  LEFT JOIN kgs_user_info AS kgs ON discord.discord_id = kgs.discord_id
  LEFT JOIN ogs_user_info AS ogs ON discord.discord_id = ogs.discord_id
  LEFT JOIN fox_user_info AS fox ON discord.discord_id = fox.discord_id
  LEFT JOIN igs_user_info AS igs ON discord.discord_id = igs.discord_id
  LEFT JOIN ffg_user_info AS ffg ON discord.discord_id = ffg.discord_id
  LEFT JOIN egf_user_info AS egf ON discord.discord_id = egf.discord_id

-- FGC

DROP TABLE IF EXISTS `fgc_validity`;
CREATE TABLE `fgc_validity` (
  `discord_id` VARCHAR(255) NOT NULL,
  `total_games` INT(11) NOT NULL,
  `total_ranked_games` INT(11) NOT NULL,
  `gold_games` INT(11) NOT NULL,
  `gold_ranked_games` INT(11) NOT NULL,
  `updated` DATETIME NULL,
  `error` TINYINT(1) NOT NULL,
  PRIMARY KEY (`discord_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO `fgc_validity`
  SELECT u.discord_id, 0 AS `total_games`, 0 AS `total_ranked_games`,  0 AS `gold_games`, 0 AS `gold_ranked_games`, NULL AS `updated`, 0 AS `error`
  FROM `users` AS u
  WHERE `discord_id` IS NOT NULL;

DROP VIEW IF EXISTS `fgc_validity_games`;
CREATE VIEW `fgc_validity_games` AS
  SELECT `game`.`gold_id`, `black`.`discord_id` AS `black_discord_id`, `white`.`discord_id` AS `white_discord_id`,`game`.`ranked`
  FROM `ogs_games` AS `game`
  LEFT JOIN `ogs_user_info` AS `black` ON `game`.`black_id` = `black`.`ogs_id`
  LEFT JOIN `ogs_user_info` AS `white` ON `game`.`white_id` = `white`.`ogs_id`
  WHERE DATEDIFF(NOW(), `date`) <= 30 AND `size` = 19 AND `handicap` = 0 AND `result` != "unfinished" AND 6 < `komi` AND `komi` < 9
  UNION
  SELECT `game`.`gold_id`, `black`.`discord_id` AS `black_discord_id`, `white`.`discord_id` AS `white_discord_id`, `game`.`ranked`
  FROM `kgs_games` AS `game`
  LEFT JOIN `kgs_user_info` AS `black` ON `game`.`black_id` = `black`.`kgs_id`
  LEFT JOIN `kgs_user_info` AS `white` ON `game`.`white_id` = `white`.`kgs_id`
  WHERE DATEDIFF(NOW(), `date`) <= 30 AND `size` = 19 AND `handicap` = 0 AND `result` != "unfinished" AND 6 < `komi` AND `komi` < 9;
