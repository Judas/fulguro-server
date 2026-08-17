-- Houses: the player picks their house. One new column on `house_members`, and no other object touched.
--
-- Players used to be drawn into one of the emptiest houses, both when joining mid-season and when a `CHANGE` was
-- applied on 1 September. Both now take the house from the request, so a `CHANGE` has to remember which one, and
-- `pending_action` alone cannot say it. That is the whole of this migration.
--
-- APPLY THIS **BEFORE** DEPLOYING THE JAR, and not the other way round -- this is the first house migration where the
-- order matters:
--
--   * The column is additive and the running jar ignores it. Every read of `house_members` is a `SELECT *` mapped with
--     `throwOnMappingFailure(false)`, so an unknown column is dropped rather than fatal, and no live statement writes
--     it. The old jar therefore keeps working with the column in place.
--   * The new jar, without the column, does NOT keep working. It names `pending_house_id` in three statements:
--     `POST /gold/api/house/choice` would answer 500, and HouseSeasonService would throw on every tick from
--     1 September -- taking the daily ranking and the season opening down with it.
--
--   mysql -u <db.user> -p fg_dev  < "doc/migration choix de maison.sql"
--   mysql -u <db.user> -p fg_prod < "doc/migration choix de maison.sql"
--
-- Run it as the DB user that owns the existing views (db.user in config.properties), as with the other migrations.
--
-- Re-runnable: the ALTER is guarded on information_schema, because MySQL has no `ADD COLUMN IF NOT EXISTS` and a second
-- plain run would fail on `Duplicate column name`. The UPDATE below is idempotent by construction.

-- ---------------------------------------------------------------------------------------------------------------
-- The column
--
-- NULL on every intention but `CHANGE`, and NULL on a `CHANGE` means the destination is unknowable rather than "pick
-- one for me": there is no draw left to fall back on, so the season opening leaves such a member in their house and
-- logs it. No foreign key to `houses`, consistent with the four other house tables -- a house id that no longer names
-- a house is read as no destination at all, not as a broken row.
SET @add_pending_house_id := (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `house_members` ADD COLUMN `pending_house_id` INT(11) NULL AFTER `pending_action`',
    -- The no-op of a second run. A `SELECT` and not a `DO`, only because every MySQL version permits this one in a
    -- prepared statement without having to check which.
    'SELECT 1'
  )
  FROM `information_schema`.`COLUMNS`
  WHERE `TABLE_SCHEMA` = DATABASE()
    AND `TABLE_NAME` = 'house_members'
    AND `COLUMN_NAME` = 'pending_house_id'
);
PREPARE add_pending_house_id FROM @add_pending_house_id;
EXECUTE add_pending_house_id;
DEALLOCATE PREPARE add_pending_house_id;

-- ---------------------------------------------------------------------------------------------------------------
-- The intentions recorded before the column existed
--
-- A `CHANGE` recorded by the old jar names no house and never can: it was recorded on the promise of a draw that no
-- longer happens. Left alone, the member would keep their house on 1 September without ever being told why, so the
-- intention is dropped instead and they are back to "has not chosen yet" -- which is the state the website asks the
-- question from. Deployed during a summer break, that leaves them until 31 August to answer it again.
--
-- Look before you clear, if this is prod and the break is under way:
--
--   SELECT `discord_id`, `house_id` FROM `house_members`
--     WHERE `pending_action` = 'CHANGE' AND `pending_house_id` IS NULL;
--
-- `STAY` and `LEAVE` are untouched: neither ever had a destination, so neither lost anything.
UPDATE `house_members`
  SET `pending_action` = NULL
  WHERE `pending_action` = 'CHANGE' AND `pending_house_id` IS NULL;

-- ---------------------------------------------------------------------------------------------------------------
-- Verification
--
-- The first must list `pending_house_id` as an INT, nullable, right after `pending_action`. The second must return 0
-- -- no `CHANGE` without a destination -- both immediately and at any point later, since the API refuses to record
-- one.
--
--   SHOW COLUMNS FROM `house_members`;
--   SELECT COUNT(*) FROM `house_members` WHERE `pending_action` = 'CHANGE' AND `pending_house_id` IS NULL;
