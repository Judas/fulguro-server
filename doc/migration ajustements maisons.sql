-- Houses: an administrator can give or take house points by hand. One new table, nothing else touched.
--
-- What the scanner cannot see -- an event, a tournament, a penalty -- goes in `house_adjustments`, one row per grant,
-- signed: a bonus is positive, a penalty negative, never 0. Each row carries the reason and the administrator, which
-- is the whole reason it is not a synthetic `house_points` row: the register is one row per (game, player) with a
-- positive `total`, and has nowhere to keep either.
--
-- `house_id` and `season` are frozen at write time, like the register's, so an adjustment stays with the house it
-- was given for when the player leaves it. `CleanService` never deletes these rows; `/admin/purge` does.
--
-- Every house total and ranking adds the season's adjustments on top of the register. A house or a player can
-- therefore now sit below zero.
--
-- APPLY THIS **BEFORE** DEPLOYING THE JAR:
--
--   * Additive for the running jar, which never names the table.
--   * The new jar needs it: every house ranking and total joins it, so without it every house route answers 500.
--
-- Apply on `fg_dev` first, then on `fg_prod`. Idempotent: `IF NOT EXISTS`.

CREATE TABLE IF NOT EXISTS `house_adjustments` (
  `id` INT(11) NOT NULL AUTO_INCREMENT,
  `discord_id` VARCHAR(255) NOT NULL,
  `house_id` INT(11) NOT NULL,
  `season` VARCHAR(9) NOT NULL,
  `points` INT(11) NOT NULL,
  `reason` VARCHAR(200) NOT NULL,
  `admin_discord_id` VARCHAR(255) NOT NULL,
  `created` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  KEY `house_adjustments_season_house` (`season`, `house_id`),
  KEY `house_adjustments_season_player` (`season`, `discord_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- Check:
--   SHOW CREATE TABLE `house_adjustments`;
--   SELECT season, discord_id, house_id, points, reason, admin_discord_id, created
--     FROM `house_adjustments` ORDER BY created DESC;
