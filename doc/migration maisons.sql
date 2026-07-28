-- Houses ("Maisons"): the four-clan team competition. Step 0 of `doc/plan-maisons.md`.
--
-- APPLY THIS ANY TIME BEFORE DEPLOYING. Everything here is purely additive -- four new tables, one new view, four
-- reference rows -- and nothing the running jar reads or writes is touched, so 8.8 keeps working unchanged with these
-- objects in place. The jar that needs them is the one that ships HouseModule; until then they just sit there empty.
--
-- Test it on fg_dev first, then apply the same file to fg_prod at deploy time:
--
--   mysql -u <db.user> -p fg_dev  < "doc/migration maisons.sql"
--   mysql -u <db.user> -p fg_prod < "doc/migration maisons.sql"
--
-- Run it as the DB user that owns the existing views (db.user in config.properties). The view below deliberately
-- carries no DEFINER clause so that the definer stays whoever runs this; naming another one would need SUPER on
-- MySQL 5.7 anyway.
--
-- Every statement is re-runnable AND non-destructive, which is not the same thing. `doc/plan-maisons.md` writes the
-- tables as DROP TABLE IF EXISTS + CREATE TABLE; that is re-runnable, but re-running it mid-season would silently
-- delete every point earned so far and every house membership. CREATE TABLE IF NOT EXISTS is used instead: a second
-- run is a no-op on an existing table. The trade-off is that it will NOT pick up a later change to a column -- if one
-- of these definitions ever changes, that needs its own ALTER in its own migration file, not an edit here.
--
-- The seed is INSERT ... ON DUPLICATE KEY UPDATE for the same reason: re-running refreshes the RP text of the four
-- houses without touching anything else, and without failing on the primary key.

-- ---------------------------------------------------------------------------------------------------------------
-- Tables
-- ---------------------------------------------------------------------------------------------------------------

-- The four houses and their RP. `slug` is the machine key: it is what the API exposes and what the website builds
-- the crest filename from, so it is stable and unique. `name` is display-only and can change without breaking
-- anything. `color` is the hex string including the leading '#', hence VARCHAR(7).
CREATE TABLE IF NOT EXISTS `houses` (
  `id` INT(11) NOT NULL,
  `slug` VARCHAR(64) NOT NULL,
  `name` VARCHAR(255) NOT NULL,
  `tagline` VARCHAR(255) NOT NULL,
  `color` VARCHAR(7) NOT NULL,
  `description` TEXT NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `houses_slug` (`slug`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- Who belongs to which house. One house at most per player, which is what the PK on `discord_id` enforces; leaving a
-- house is deleting the row.
--
-- `joined` is what stops retroactive scoring: the scanner only credits games dated at or after it, so a player who
-- joins in November earns nothing on the October games still inside CleanService's 32-day window.
--
-- `pending_action` is an intention recorded during the summer, not something applied on the spot: NULL, 'STAY',
-- 'CHANGE' or 'LEAVE'. HouseSeasonService applies it when the next season opens and resets it to NULL. Left as
-- VARCHAR rather than an ENUM so that adding a fourth choice stays a code change, not a DDL change on prod.
CREATE TABLE IF NOT EXISTS `house_members` (
  `discord_id` VARCHAR(255) NOT NULL,
  `house_id` INT(11) NOT NULL,
  `joined` DATETIME NOT NULL,
  `pending_action` VARCHAR(16) NULL,
  PRIMARY KEY (`discord_id`),
  KEY `house_members_house` (`house_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- The points register: one row per (game, player), with the breakdown per bonus type as it was scored.
--
-- The PK `(gold_id, discord_id)` is the whole of the idempotence. A game cannot be counted twice for the same player
-- however many times the scanner walks over it, so the scanner needs no cursor, no "scored" flag and no transaction.
--
-- `house_id` is frozen at write time, on purpose: a player changing house never moves the points they already earned,
-- so a house total only ever grows. Same for `season` -- the register is the history, and every read filters on it.
--
-- No foreign key, also on purpose: CleanService deletes games after 32 days and members when they leave the Discord
-- server, and these rows have to outlive both. A FK would either block those deletes or cascade the points away.
--
-- `scored_at` earns its place even though nothing reads it yet: with no anti-farming cap in this delivery, it plus
-- `gold_id` is what makes a cap computable after the fact, mid-season, without a migration.
--
-- ROW_FORMAT is stated rather than inherited because that PK is wide: 2 x VARCHAR(255) in utf8mb4 is 2040 bytes,
-- which fits the 3072-byte index limit of DYNAMIC but blows through the 767 bytes of COMPACT. If this still fails
-- with error 1071, the server has innodb_large_prefix off and the fix is a narrower `discord_id`, not another
-- ROW_FORMAT -- a Discord snowflake is 20 characters, so VARCHAR(255) is already 12x more than it needs.
CREATE TABLE IF NOT EXISTS `house_points` (
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

-- The once-only guard for everything that must happen exactly once per season: applying the summer intentions,
-- announcing the closing recap, posting the daily ranking. Same job as the old Exam Hunter's `hasPromotionScore`,
-- kept in a table rather than deduced, because HouseSeasonService ticks every 10 minutes and a 7am-9am window is 18
-- ticks wide -- without `last_ranking` that is 18 identical Discord messages.
--
-- `opened` NULL means the season has a row but has never started, which is also what tells the closing branch apart
-- from a false start: deployed in July 2026, HouseSeason.seasonName() answers '2025-2026', a season that never
-- existed here, and closing it would announce the results of an empty competition.
--
-- `season` is the '2026-2027' string computed in Kotlin, hence VARCHAR(9).
CREATE TABLE IF NOT EXISTS `house_seasons` (
  `season` VARCHAR(9) NOT NULL,
  `opened` DATETIME NULL,
  `closed` DATETIME NULL,
  `last_ranking` DATETIME NULL,
  PRIMARY KEY (`season`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ---------------------------------------------------------------------------------------------------------------
-- View
--
-- The live views carry ALGORITHM = UNDEFINED and SQL SECURITY DEFINER, owned by the DB user the app connects with.
-- CREATE OR REPLACE rather than DROP + CREATE: the replace is an atomic metadata swap, so no scanner tick can land
-- in a window where the view does not exist.
-- ---------------------------------------------------------------------------------------------------------------

-- Every finished game either platform stores, flattened to the two Discord ids, for the scanner to score.
--
-- LEFT JOIN, not JOIN, and this is the load-bearing part: an opponent unknown to the server has to come back with a
-- NULL discord_id, because that null is exactly what tells the `gold_opponent` bonus apart from no bonus at all.
-- Turn these into inner joins and every game against an outsider disappears from the register instead of scoring 1
-- point for the member who played it.
--
-- `handicap` is here for the "partie à égalité" bonus, which is handicap = 0 and not a drawn result. No `size` and
-- no `komi`, because the agreed scoring filters on neither -- unlike fgc_validity_games, which is the same union
-- narrowed to 19x19, no handicap, standard komi and 30 days. If a filter is ever wanted, adding the column here is
-- the whole change: connection.query() derives snake_case onto camelCase, so nothing needs registering anywhere.
--
-- UNION, not UNION ALL, to stay consistent with the three views next door. The dedupe pass costs nothing here: the
-- two branches produce disjoint gold_ids by construction ('OGS_...' vs 'KGS_...'), and CleanService keeps the games
-- tables down to about a month.
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

-- ---------------------------------------------------------------------------------------------------------------
-- Reference data
--
-- The four houses, from `assets/maisons.md`. French, because this is content players read on the website.
--
-- Two apostrophes are normalised against that file: it mixes the typographic ’ with a straight ' in "Lunaires
-- d'Æther" and in "l'efficacité maximale", and player-facing text should not be inconsistent inside one paragraph.
-- Everything else is verbatim. Side effect worth knowing: with no straight apostrophe left there is nothing to
-- escape in these string literals, so a stray '' in a later edit is a real syntax error rather than silent data.
--
-- The framing story ("La chute de l'Harmonie") is deliberately not stored: it belongs to no single house and lives
-- in the website repository.
-- ---------------------------------------------------------------------------------------------------------------

INSERT INTO `houses` (`id`, `slug`, `name`, `tagline`, `color`, `description`) VALUES
  (1, 'FILS_DU_FROID', 'Fils du Froid', 'Le meilleur coup est celui qui brise.', '#740001',
   'Nés sous les vents du nord, les membres des Fils du Froid embrassent le feu du combat. Chaque pierre posée est une déclaration de guerre. Pour eux, le plateau est un champ de bataille, et la victoire ne s’offre qu’aux plus audacieux. Aucun territoire n’est défendu s’il peut être conquis. Leur style est impétueux, leur esprit, indomptable.'),
  (2, 'NEXUS_ALPHA', 'Nexus Alpha', 'Chaque coup est une équation.', '#0E1A40',
   'Nexus Alpha analyse, anticipe, optimise. Ici, l’intuition est assistée par la froideur mathématique. Chaque partie est une simulation, chaque mouvement calculé pour tendre vers l’efficacité maximale. Ils cherchent la ligne idéale dans le chaos apparent, convaincus que la vérité du Go réside dans la logique pure et la maîtrise des séquences.'),
  (3, 'SABRE_SILENCIEUX', 'Sabre Silencieux', 'Un coup, un destin !', '#1A472A',
   'Fidèles au bushido, les membres du Sabre Silencieux considèrent le Go comme un art martial spirituel. La beauté d’un joseki maîtrisé vaut mieux qu’un triomphe désordonné. Ils ne trahissent jamais leurs principes : respect de l’adversaire, discipline du jeu, et harmonie dans les formes. Leur calme est leur plus puissante arme.'),
  (4, 'LUNAIRES_AETHER', 'Lunaires d’Æther', 'Pourquoi jouer comme hier ?', '#B85209',
   'Curieux, imprévisibles, parfois déconcertants, les Lunaires d’Æther refusent la voie tracée. Leurs coups sont des inventions, leurs ouvertures, des prototypes. Ils collectionnent les formes étranges et les variations absurdes qui finissent, parfois, par fonctionner. Chaque partie est un laboratoire, chaque joueur, un inventeur de l’impossible.')
ON DUPLICATE KEY UPDATE
  `slug` = VALUES(`slug`), `name` = VALUES(`name`), `tagline` = VALUES(`tagline`),
  `color` = VALUES(`color`), `description` = VALUES(`description`);

-- ---------------------------------------------------------------------------------------------------------------
-- Checks
--
-- The first must return 4 rows with readable accents -- 'NÃ©s' instead of 'Nés' means the client connected in latin1,
-- so the text is mojibake in the table and has to be re-inserted, not just re-read.
--
-- The second must return a plausible number of games, in the same order of magnitude as api_games. Zero means the
-- view is wrong or both games tables are empty; on fg_dev, check which of the two it is before blaming the view.
--
-- The third must return 0 on a fresh install. It is the scanner's selection query without the house_members join,
-- and on fg_prod it will stay 0 until someone actually joins a house.
--
--   SELECT `id`, `slug`, `name`, `color`, LEFT(`description`, 40) FROM `houses` ORDER BY `id`;
--   SELECT COUNT(*) FROM `house_games`;
--   SELECT COUNT(*) FROM `house_games` AS `g`
--     LEFT JOIN `house_points` AS `p` ON `p`.`gold_id` = `g`.`gold_id` WHERE `p`.`gold_id` IS NULL
--     AND `g`.`date` >= '2026-09-01';
--
-- And, if this ran against fg_dev, confirm the objects landed there and not next door:
--
--   SELECT TABLE_NAME, TABLE_TYPE FROM information_schema.TABLES
--     WHERE TABLE_SCHEMA = 'fg_dev' AND TABLE_NAME LIKE 'house%';
