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
  SELECT u.discord_id, 0 AS `rating`, 1 AS `tier_rank`, NULL AS `updated`, 0 AS `error`
  FROM `users` AS u
  WHERE `discord_id` IS NOT NULL;

DROP VIEW IF EXISTS `gold_ranks`;
CREATE VIEW `gold_ranks` AS
  SELECT `discord`.`discord_id`, `kgs_rank`, `ogs_rank`, `fox_rank`, `igs_rank`, `ffg_rank`, `egf_rank`,
  (IF(kgs.error IS NULL,0,kgs.error) + IF(ogs.error IS NULL,0,ogs.error) + IF(fox.error IS NULL,0,fox.error) + IF(igs.error IS NULL,0,igs.error) + IF(ffg.error IS NULL,0,ffg.error) + IF(egf.error IS NULL,0,egf.error)) AS error
  FROM `discord_user_info` AS `discord`
  LEFT JOIN `kgs_user_info` AS `kgs` ON `discord`.`discord_id` = `kgs`.`discord_id`
  LEFT JOIN `ogs_user_info` AS `ogs` ON `discord`.`discord_id` = `ogs`.`discord_id`
  LEFT JOIN `fox_user_info` AS `fox` ON `discord`.`discord_id` = `fox`.`discord_id`
  LEFT JOIN `igs_user_info` AS `igs` ON `discord`.`discord_id` = `igs`.`discord_id`
  LEFT JOIN `ffg_user_info` AS `ffg` ON `discord`.`discord_id` = `ffg`.`discord_id`
  LEFT JOIN `egf_user_info` AS `egf` ON `discord`.`discord_id` = `egf`.`discord_id`;

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

-- API

DROP VIEW IF EXISTS `api_players`;
CREATE VIEW `api_players` AS
  SELECT
  `discord`.`discord_id`, `discord`.`discord_name`, `discord`.`discord_avatar`,
  `kgs_id`, `kgs_rank`, 
  `ogs_id`, `ogs_name`, `ogs_rank`, 
  `fox_id`, `fox_name`, `fox_rank`, 
  `igs_id`, `igs_rank`, 
  `ffg_id`, `ffg_name`, `ffg_rank`, 
  `egf_id`, `egf_name`, `egf_rank`,
  `gold`.`rating`, `gold`.`tier_rank`, `tier`.`name` AS `tier_name`,
  `fgc`.`total_ranked_games`, `fgc`.`gold_ranked_games`
  FROM `discord_user_info` AS `discord`
  LEFT JOIN `gold_ratings` AS `gold` ON `gold`.`discord_id` = `discord`.`discord_id`
  LEFT JOIN `gold_tiers` AS `tier` ON `gold`.`tier_rank` = `tier`.`rank`
  LEFT JOIN `fgc_validity` AS `fgc` ON `fgc`.`discord_id` = `discord`.`discord_id`
  LEFT JOIN `kgs_user_info` AS `kgs` ON `discord`.`discord_id` = `kgs`.`discord_id`
  LEFT JOIN `ogs_user_info` AS `ogs` ON `discord`.`discord_id` = `ogs`.`discord_id`
  LEFT JOIN `fox_user_info` AS `fox` ON `discord`.`discord_id` = `fox`.`discord_id`
  LEFT JOIN `igs_user_info` AS `igs` ON `discord`.`discord_id` = `igs`.`discord_id`
  LEFT JOIN `ffg_user_info` AS `ffg` ON `discord`.`discord_id` = `ffg`.`discord_id`
  LEFT JOIN `egf_user_info` AS `egf` ON `discord`.`discord_id` = `egf`.`discord_id`;

DROP VIEW IF EXISTS `api_games`;
CREATE VIEW `api_games` AS
  SELECT `game`.`gold_id`, `game`.`date`, `game`.`result`, `game`.`sgf`,
  `black_discord`.`discord_id` AS `black_discord_id`, `black_discord`.`discord_name` AS `black_discord_name`, `black_discord`.`discord_avatar` AS `black_discord_avatar`,
  `black_gold`.`rating` AS `black_rating`, `black_gold`.`tier_rank` AS `black_tier_rank`, `black_tier`.`name` AS `black_tier_name`,
  `white_discord`.`discord_id` AS `white_discord_id`, `white_discord`.`discord_name` AS `white_discord_name`, `white_discord`.`discord_avatar` AS `white_discord_avatar`,
  `white_gold`.`rating` AS `white_rating`, `white_gold`.`tier_rank` AS `white_tier_rank`, `white_tier`.`name` AS `white_tier_name`
  FROM `ogs_games` AS `game`
  INNER JOIN `ogs_user_info` AS `black_ogs` ON `game`.`black_id` = `black_ogs`.`ogs_id`
  INNER JOIN `discord_user_info` AS `black_discord` ON `black_ogs`.`discord_id` = `black_discord`.`discord_id`
  INNER JOIN `gold_ratings` AS `black_gold` ON `black_discord`.`discord_id` = `black_gold`.`discord_id`
  INNER JOIN `gold_tiers` AS `black_tier` ON `black_gold`.`tier_rank` = `black_tier`.`rank`
  INNER JOIN `ogs_user_info` AS `white_ogs` ON `game`.`white_id` = `white_ogs`.`ogs_id`
  INNER JOIN `discord_user_info` AS `white_discord` ON `white_ogs`.`discord_id` = `white_discord`.`discord_id`
  INNER JOIN `gold_ratings` AS `white_gold` ON `white_discord`.`discord_id` = `white_gold`.`discord_id`
  INNER JOIN `gold_tiers` AS `white_tier` ON `white_gold`.`tier_rank` = `white_tier`.`rank`
  WHERE `result` != "unfinished"
  UNION
  SELECT `game`.`gold_id`, `game`.`date`, `game`.`result`, `game`.`sgf`,
  `black_discord`.`discord_id` AS `black_discord_id`, `black_discord`.`discord_name` AS `black_discord_name`, `black_discord`.`discord_avatar` AS `black_discord_avatar`,
  `black_gold`.`rating` AS `black_rating`, `black_gold`.`tier_rank` AS `black_tier_rank`, `black_tier`.`name` AS `black_tier_name`,
  `white_discord`.`discord_id` AS `white_discord_id`, `white_discord`.`discord_name` AS `white_discord_name`, `white_discord`.`discord_avatar` AS `white_discord_avatar`,
  `white_gold`.`rating` AS `white_rating`, `white_gold`.`tier_rank` AS `white_tier_rank`, `white_tier`.`name` AS `white_tier_name`
  FROM `kgs_games` AS `game`
  INNER JOIN `kgs_user_info` AS `black_kgs` ON `game`.`black_id` = `black_kgs`.`kgs_id`
  INNER JOIN `discord_user_info` AS `black_discord` ON `black_kgs`.`discord_id` = `black_discord`.`discord_id`
  INNER JOIN `gold_ratings` AS `black_gold` ON `black_discord`.`discord_id` = `black_gold`.`discord_id`
  INNER JOIN `gold_tiers` AS `black_tier` ON `black_gold`.`tier_rank` = `black_tier`.`rank`
  INNER JOIN `kgs_user_info` AS `white_kgs` ON `game`.`white_id` = `white_kgs`.`kgs_id`
  INNER JOIN `discord_user_info` AS `white_discord` ON `white_kgs`.`discord_id` = `white_discord`.`discord_id`
  INNER JOIN `gold_ratings` AS `white_gold` ON `white_discord`.`discord_id` = `white_gold`.`discord_id`
  INNER JOIN `gold_tiers` AS `white_tier` ON `white_gold`.`tier_rank` = `white_tier`.`rank`
  WHERE `result` != "unfinished"
  UNION
  SELECT `game`.`gold_id`, `game`.`date`, `game`.`result`, `game`.`sgf`,
  `black_discord`.`discord_id` AS `black_discord_id`, `black_discord`.`discord_name` AS `black_discord_name`, `black_discord`.`discord_avatar` AS `black_discord_avatar`,
  `black_gold`.`rating` AS `black_rating`, `black_gold`.`tier_rank` AS `black_tier_rank`, `black_tier`.`name` AS `black_tier_name`,
  `white_discord`.`discord_id` AS `white_discord_id`, `white_discord`.`discord_name` AS `white_discord_name`, `white_discord`.`discord_avatar` AS `white_discord_avatar`,
  `white_gold`.`rating` AS `white_rating`, `white_gold`.`tier_rank` AS `white_tier_rank`, `white_tier`.`name` AS `white_tier_name`
  FROM `fox_games` AS `game`
  INNER JOIN `fox_user_info` AS `black_fox` ON `game`.`black_id` = `black_fox`.`fox_id`
  INNER JOIN `discord_user_info` AS `black_discord` ON `black_fox`.`discord_id` = `black_discord`.`discord_id`
  INNER JOIN `gold_ratings` AS `black_gold` ON `black_discord`.`discord_id` = `black_gold`.`discord_id`
  INNER JOIN `gold_tiers` AS `black_tier` ON `black_gold`.`tier_rank` = `black_tier`.`rank`
  INNER JOIN `fox_user_info` AS `white_fox` ON `game`.`white_id` = `white_fox`.`fox_id`
  INNER JOIN `discord_user_info` AS `white_discord` ON `white_fox`.`discord_id` = `white_discord`.`discord_id`
  INNER JOIN `gold_ratings` AS `white_gold` ON `white_discord`.`discord_id` = `white_gold`.`discord_id`
  INNER JOIN `gold_tiers` AS `white_tier` ON `white_gold`.`tier_rank` = `white_tier`.`rank`
  WHERE `result` != "unfinished";

-- HOUSES

DROP TABLE IF EXISTS `fgo_houses`;
CREATE TABLE `fgo_houses` (
  `id` INT(11) NOT NULL,
  `color` VARCHAR(255) NOT NULL,
  `name` VARCHAR(255) NOT NULL,
  `headline` VARCHAR(255) NOT NULL,
  `welcome` VARCHAR(255) NOT NULL,
  `description` TEXT NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO `fgo_houses`(`id`,`color`,`name`,`headline`,`welcome`,`description`)
VALUES
('1','#740001','Fils du Froid','Le meilleur coup est celui qui brise.', 'Nous jouons comme nous vivons : en conquérants.', "Nés sous les vents du nord, les membres des Fils du Froid embrassent le feu du combat. Chaque pierre posée est une déclaration de guerre. Pour eux, le plateau est un champ de bataille, et la victoire ne s'offre qu'aux plus audacieux. Aucun territoire n'est défendu s'il peut être conquis. Leur style est impétueux, leur esprit, indomptable."),
('2','#7DFFFC','Nexus Alpha','Chaque coup est une équation.', 'Nous ne jouons pas : nous calculons la victoire.', "Nexus Alpha analyse, anticipe, optimise. Ici, l’intuition est assistée par la froideur mathématique. Chaque partie est une simulation, chaque mouvement calculé pour tendre vers l'efficacité maximale. Ils cherchent la ligne idéale dans le chaos apparent, convaincus que la vérité du Go réside dans la logique pure et la maîtrise des séquences."),
('3','#1A472A','Sabre Silencieux','Un coup, un destin !', 'Plutôt la capture que le déshonneur.', "Fidèles au bushido, les membres du Sabre Silencieux considèrent le Go comme un art martial spirituel. La beauté d’un joseki maîtrisé vaut mieux qu’un triomphe désordonné. Ils ne trahissent jamais leurs principes : respect de l’adversaire, discipline du jeu, et harmonie dans les formes. Leur calme est leur plus puissante arme."),
('4','#B85209',"Lunaires d'Æther",'Pourquoi jouer comme hier ?', 'Le génie est une séquence hasardeuse qui réussit.', "Curieux, imprévisibles, parfois déconcertants, les Lunaires d’Æther refusent la voie tracée. Leurs coups sont des inventions, leurs ouvertures, des prototypes. Ils collectionnent les formes étranges et les variations absurdes qui finissent, parfois, par fonctionner. Chaque partie est un laboratoire, chaque joueur, un inventeur de l’impossible.");

DROP TABLE IF EXISTS `fgo_house_points`;
CREATE TABLE `fgo_house_points` (
  `discord_id` VARCHAR(255) NOT NULL,
  `house_id` INT(11) NOT NULL,
  `played` INT(11) NOT NULL,
  `gold` INT(11) NOT NULL,
  `house` INT(11) NOT NULL,
  `win` INT(11) NOT NULL,
  `long` INT(11) NOT NULL,
  `balanced` INT(11) NOT NULL,
  `ranked` INT(11) NOT NULL,
  `fgc` INT(11) NOT NULL,
  `check_date` DATETIME NULL,
  `updated` DATETIME NULL,
  `error` TINYINT(1) NOT NULL,
  PRIMARY KEY (`discord_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP TABLE IF EXISTS `backup_houses_points`;
CREATE TABLE `backup_houses_points` (
  `date` DATETIME NOT NULL,
  `discord_id` VARCHAR(255) NOT NULL,
  `house_id` INT(11) NOT NULL,
  `played` INT(11) NOT NULL,
  `gold` INT(11) NOT NULL,
  `house` INT(11) NOT NULL,
  `win` INT(11) NOT NULL,
  `long` INT(11) NOT NULL,
  `balanced` INT(11) NOT NULL,
  `ranked` INT(11) NOT NULL,
  `fgc` INT(11) NOT NULL,
  `check_date` DATETIME NULL,
  `updated` DATETIME NULL,
  `error` TINYINT(1) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP EVENT IF EXISTS `backup_houses_points_event`;
DELIMITER |
CREATE EVENT `backup_houses_points_event`
    ON SCHEDULE
      EVERY 1 DAY
    DO
      BEGIN
        INSERT INTO `backup_houses_points`
          SELECT NOW() AS `date`, points.* FROM `fgo_house_points` AS points;
        DELETE FROM `backup_houses_points` WHERE DATEDIFF(NOW(), date) > 15;
      END |
DELIMITER ;

DROP VIEW IF EXISTS `fgo_houses_games`;
CREATE VIEW `fgo_houses_games` AS
  SELECT `game`.`gold_id`, `game`.`date`, `game`.`result`, `game`.`long_game`, `game`.`handicap`, `game`.`komi`, `game`.`ranked`,
  `black_discord`.`discord_id` AS `black_discord_id`, `black_house`.`house_id` AS `black_house_id`,
  `white_discord`.`discord_id` AS `white_discord_id`, `white_house`.`house_id` AS `white_house_id`
  FROM `ogs_games` AS `game`
  LEFT JOIN `ogs_user_info` AS `black_ogs` ON `game`.`black_id` = `black_ogs`.`ogs_id`
  LEFT JOIN `discord_user_info` AS `black_discord` ON `black_ogs`.`discord_id` = `black_discord`.`discord_id`
  LEFT JOIN `fgo_house_points` AS `black_house` ON `black_ogs`.`discord_id` = `black_house`.`discord_id`
  LEFT JOIN `ogs_user_info` AS `white_ogs` ON `game`.`white_id` = `white_ogs`.`ogs_id`
  LEFT JOIN `discord_user_info` AS `white_discord` ON `white_ogs`.`discord_id` = `white_discord`.`discord_id`
  LEFT JOIN `fgo_house_points` AS `white_house` ON `white_ogs`.`discord_id` = `white_house`.`discord_id`
  WHERE `result` != "unfinished"
  UNION
  SELECT `game`.`gold_id`, `game`.`date`, `game`.`result`, `game`.`long_game`, `game`.`handicap`, `game`.`komi`, `game`.`ranked`,
  `black_discord`.`discord_id` AS `black_discord_id`, `black_house`.`house_id` AS `black_house_id`,
  `white_discord`.`discord_id` AS `white_discord_id`, `white_house`.`house_id` AS `white_house_id`
  FROM `ogs_games` AS `game`
  LEFT JOIN `kgs_user_info` AS `black_kgs` ON `game`.`black_id` = `black_kgs`.`kgs_id`
  LEFT JOIN `discord_user_info` AS `black_discord` ON `black_kgs`.`discord_id` = `black_discord`.`discord_id`
  LEFT JOIN `fgo_house_points` AS `black_house` ON `black_kgs`.`discord_id` = `black_house`.`discord_id`
  LEFT JOIN `kgs_user_info` AS `white_kgs` ON `game`.`white_id` = `white_kgs`.`kgs_id`
  LEFT JOIN `discord_user_info` AS `white_discord` ON `white_kgs`.`discord_id` = `white_discord`.`discord_id`
  LEFT JOIN `fgo_house_points` AS `white_house` ON `white_kgs`.`discord_id` = `white_house`.`discord_id`
  WHERE `result` != "unfinished"
  UNION
  SELECT `game`.`gold_id`, `game`.`date`, `game`.`result`, `game`.`long_game`, `game`.`handicap`, `game`.`komi`, `game`.`ranked`,
  `black_discord`.`discord_id` AS `black_discord_id`, `black_house`.`house_id` AS `black_house_id`,
  `white_discord`.`discord_id` AS `white_discord_id`, `white_house`.`house_id` AS `white_house_id`
  FROM `ogs_games` AS `game`
  LEFT JOIN `fox_user_info` AS `black_fox` ON `game`.`black_id` = `black_fox`.`fox_id`
  LEFT JOIN `discord_user_info` AS `black_discord` ON `black_fox`.`discord_id` = `black_discord`.`discord_id`
  LEFT JOIN `fgo_house_points` AS `black_house` ON `black_fox`.`discord_id` = `black_house`.`discord_id`
  LEFT JOIN `fox_user_info` AS `white_fox` ON `game`.`white_id` = `white_fox`.`fox_id`
  LEFT JOIN `discord_user_info` AS `white_discord` ON `white_fox`.`discord_id` = `white_discord`.`discord_id`
  LEFT JOIN `fgo_house_points` AS `white_house` ON `white_fox`.`discord_id` = `white_house`.`discord_id`
  WHERE `result` != "unfinished"
