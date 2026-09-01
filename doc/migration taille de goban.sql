-- Houses: the board size finally counts. One new column on `house_points`, and `house_games` gains `size`.
--
-- Until now every finished game scored the same scale whatever it was played on, so a handful of 9x9 blitz earned as
-- much as the same number of 19x19. From now on the scale is computed as before and then divided by the board:
--
--   * 19x19 -- untouched, the total stands.
--   * 13x13 -- total divided by 2, rounded UP.
--   * 9x9   -- total divided by 4, rounded UP.
--   * anything else -- not scored at all. The game is filtered out of the scanner's selection and never enters the
--     register, rather than entering it worth nothing.
--
-- Rounding up, and not down, is why the divided total cannot be spread over the seven scoring columns: ceil(11/2) = 6
-- is not the sum of seven columns each rounded on its own (that gives 7). So the awarded total is now stored, once,
-- next to the breakdown that produced it -- `house_points`.`total`. On a 19x19 it equals the sum of the seven columns;
-- below that it is deliberately smaller, and the website is expected to print the seven columns as the detail of how
-- the game was judged and `total` as what it was worth.
--
-- Side effect worth knowing: `total` also retires the one duplicated copy of the scale that the code carried, the SQL
-- expression that summed the seven columns to total a house. There is nothing left to keep in step.
--
-- APPLY THIS **BEFORE** DEPLOYING THE JAR:
--
--   * Both objects are additive for the running jar. It reads `house_games` with `SELECT DISTINCT g.*` mapped with
--     `throwOnMappingFailure(false)`, so the new `size` column is dropped rather than fatal, and its INSERT into
--     `house_points` is an `INSERT IGNORE`, which demotes the missing `total` to a warning and writes the column's
--     default. So the old jar keeps scoring, at 0 in the new column -- see the repair below.
--   * The new jar, without them, does NOT work. It names `g.size` in the scanner's selection and `total` in its
--     insert: HousePointsService would throw on every tick, and the register would stop filling in silence, since a
--     failed tick is caught, logged and retried.
--
--   mysql -u <db.user> -p fg_dev  < "doc/migration taille de goban.sql"
--   mysql -u <db.user> -p fg_prod < "doc/migration taille de goban.sql"
--
-- Run it as the DB user that owns the existing views (db.user in config.properties), as with the other migrations.
--
-- Re-runnable, and meant to be run twice: once before the deploy, once after, so that the rows the old jar wrote in
-- between -- which carry total = 0 -- are repaired. The ALTER is guarded on information_schema, because MySQL has no
-- `ADD COLUMN IF NOT EXISTS`; everything else is idempotent by construction.

-- ---------------------------------------------------------------------------------------------------------------
-- The column
--
-- `DEFAULT 0` is not a plausible value, it is the marker of a row written by a jar that does not know about the
-- column. A real total is never 0: `played` is 1 on every row and the largest divisor is 4, so the smallest total the
-- scale can produce is ceil(1/4) = 1. That is what makes `WHERE total = 0` below an exact selection of the rows that
-- still need valuing, both on the first run over an existing register and on the second run after the deploy.
SET @add_total := (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `house_points` ADD COLUMN `total` INT(11) NOT NULL DEFAULT 0 AFTER `ranked`',
    -- The no-op of a second run. A `SELECT` and not a `DO`, only because every MySQL version permits this one in a
    -- prepared statement without having to check which.
    'SELECT 1'
  )
  FROM `information_schema`.`COLUMNS`
  WHERE `TABLE_SCHEMA` = DATABASE()
    AND `TABLE_NAME` = 'house_points'
    AND `COLUMN_NAME` = 'total'
);
PREPARE add_total FROM @add_total;
EXECUTE add_total;
DEALLOCATE PREPARE add_total;

-- ---------------------------------------------------------------------------------------------------------------
-- The view
--
-- `size` added to the three branches and nothing else touched. No filter here on purpose: the sizes the scale accepts
-- are named in the scanner's selection query, in Kotlin, next to the other rules that decide what gets scored. Put the
-- filter here instead and a jar deployed against an older view would find no `size` column at all and stall quietly;
-- named in the query, that same mistake is an unknown-column error on the first tick, which is the failure everyone
-- would rather have.
CREATE OR REPLACE ALGORITHM = UNDEFINED SQL SECURITY DEFINER VIEW `house_games` AS
  SELECT `game`.`gold_id`, `game`.`date`, `game`.`result`,
  `game`.`ranked`, `game`.`long_game`, `game`.`handicap`, `game`.`size`,
  `black`.`discord_id` AS `black_discord_id`, `white`.`discord_id` AS `white_discord_id`
  FROM `ogs_games` AS `game`
  LEFT JOIN `ogs_user_info` AS `black` ON `game`.`black_id` = `black`.`ogs_id`
  LEFT JOIN `ogs_user_info` AS `white` ON `game`.`white_id` = `white`.`ogs_id`
  WHERE `game`.`result` != 'unfinished'
  UNION
  SELECT `game`.`gold_id`, `game`.`date`, `game`.`result`,
  `game`.`ranked`, `game`.`long_game`, `game`.`handicap`, `game`.`size`,
  `black`.`discord_id` AS `black_discord_id`, `white`.`discord_id` AS `white_discord_id`
  FROM `kgs_games` AS `game`
  LEFT JOIN `kgs_user_info` AS `black` ON `game`.`black_id` = `black`.`kgs_id`
  LEFT JOIN `kgs_user_info` AS `white` ON `game`.`white_id` = `white`.`kgs_id`
  WHERE `game`.`result` != 'unfinished'
  UNION
  SELECT `game`.`gold_id`, `game`.`date`, `game`.`result`,
  `game`.`ranked`, `game`.`long_game`, `game`.`handicap`, `game`.`size`,
  `black`.`discord_id` AS `black_discord_id`, `white`.`discord_id` AS `white_discord_id`
  FROM `fox_games` AS `game`
  LEFT JOIN `fox_user_info` AS `black` ON `game`.`black_id` = `black`.`fox_id`
  LEFT JOIN `fox_user_info` AS `white` ON `game`.`white_id` = `white`.`fox_id`
  WHERE `game`.`result` != 'unfinished';

-- ---------------------------------------------------------------------------------------------------------------
-- The register as it stands
--
-- Every row already in it was scored at full value, so full value is what it keeps. Not a choice between two
-- readings: `house_points` has never held the board size, and `CleanService` deletes a game 32 days after it was
-- played, so the size of an older game is not recoverable from anywhere. Re-reading the register down is therefore
-- impossible for most of it, and leaving it as it was scored is the only story that is true for every row.
--
-- This is also the statement that repairs the deploy window on the second run: a row the old jar inserted between the
-- migration and the deploy has total = 0 and is valued here, at full value like the rest.
UPDATE `house_points`
  SET `total` = `played` + `gold_opponent` + `rival_house` + `long_game` + `victory` + `even_game` + `ranked`
  WHERE `total` = 0;

-- ---------------------------------------------------------------------------------------------------------------
-- OPTIONAL -- the part of the register whose games are still here
--
-- The games of the last 32 days are still in `house_games`, so for those rows -- and only those -- the size is
-- knowable and the new scale can be applied retroactively. Expect it to touch very little: a season opens on
-- 1 September, so at that date the current season's register holds a few days at most.
--
-- Skip both statements to leave the register strictly as it was scored. Run them to have the current season start
-- consistent with the rule that now governs it. They are idempotent -- the first recomputes from the breakdown
-- columns, which are never touched, and the second deletes rows that a second run no longer finds.
UPDATE `house_points` AS `p`
  JOIN `house_games` AS `g` ON `g`.`gold_id` = `p`.`gold_id`
  SET `p`.`total` = CEIL(
    (`p`.`played` + `p`.`gold_opponent` + `p`.`rival_house` + `p`.`long_game` + `p`.`victory` + `p`.`even_game` +
     `p`.`ranked`)
    / CASE `g`.`size` WHEN 13 THEN 2 WHEN 9 THEN 4 ELSE 1 END
  )
  WHERE `g`.`size` IN (9, 13, 19);

-- The games on a board the scale does not know. They should never have been in the register and the scanner will not
-- put them back: its selection now skips them, so a deleted row does not come back on the next tick.
DELETE `p` FROM `house_points` AS `p`
  JOIN `house_games` AS `g` ON `g`.`gold_id` = `p`.`gold_id`
  WHERE `g`.`size` NOT IN (9, 13, 19);

-- ---------------------------------------------------------------------------------------------------------------
-- Verification
--
-- The first must list `total` as an INT NOT NULL right after `ranked`. The second must return 0, both now and at any
-- point after the deploy -- a row worth nothing cannot be produced by the scale, so a non-zero count means a jar is
-- writing the register without knowing about the column. The third must return no row on a board other than 9, 13 or
-- 19: the scanner is not scoring what it should not. The fourth reads a sample by size, which is the one that shows
-- the coefficient actually landed -- `total` equal to the sum on 19, half of it rounded up on 13, a quarter on 9.
--
--   SHOW COLUMNS FROM `house_points`;
--   SELECT COUNT(*) FROM `house_points` WHERE `total` = 0;
--   SELECT `g`.`size`, COUNT(*) FROM `house_points` AS `p`
--     JOIN `house_games` AS `g` ON `g`.`gold_id` = `p`.`gold_id` GROUP BY `g`.`size`;
--   SELECT `g`.`size`,
--     `p`.`played` + `p`.`gold_opponent` + `p`.`rival_house` + `p`.`long_game` + `p`.`victory` + `p`.`even_game`
--       + `p`.`ranked` AS `brut`,
--     `p`.`total`
--     FROM `house_points` AS `p` JOIN `house_games` AS `g` ON `g`.`gold_id` = `p`.`gold_id`
--     ORDER BY `p`.`scored_at` DESC LIMIT 20;
