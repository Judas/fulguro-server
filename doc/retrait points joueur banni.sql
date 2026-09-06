-- Houses: removing a banned player's points from their house total.
--
-- Not a migration and not a deploy step -- no schema, no view, nothing for `release.sh` to care about. It is a one-off
-- admin correction, kept here because it is the only hand-run SQL in the project that *destroys* earned data, and the
-- order of its two DELETEs is the whole of its correctness. Written for the case it was first needed for: a player
-- banned for cheating, whose points went on counting for their house after the ban.
--
-- WHY THE POINTS SURVIVE A BAN
--
-- By design. `CleanService.removeAllFrom` purges `discord_user_info`, both platform links, `gold_ratings`,
-- `fgc_validity`, `house_members` and the two league tables -- and pointedly not `house_points`, so that a house total
-- cannot shrink when a member leaves. That rule protects points that were *earned*. It is not a licence to keep points
-- that were not, and `OgsDatabaseAccessor.removeAnnulledGames` already says so in as many words while deleting from the
-- register itself. This script is the same exception, made by hand.
--
-- The symptom is confusing, and worth naming: `houseTotals` sums `house_points` on `house_id` and `season` with no join
-- to the members, while `rankedMembers` INNER JOINs `discord_user_info`. Once the ban has cost the player their Discord
-- row, their points inflate the house total and appear in no member listing at all. Invisible and still counting.
--
-- WHAT IT TOUCHES, AND WHAT IT DOES NOT
--
-- `house_members` and `house_points` only. The games stay: they remain on the site, keep counting for the Gold rating
-- and for FGC validity, and `CleanService` deletes them 32 days after they were played like any others. Removing those
-- too is a different decision from this one -- see the last section.
--
-- ⚠ THE ONE TRAP: THE ROWS COME BACK
--
-- `HouseDatabaseAccessor.gamesToScore` marks its progress **per game, not per player**:
--
--     LEFT JOIN house_points p ON p.gold_id = g.gold_id ... WHERE p.gold_id IS NULL
--
-- So deleting the only register row a game has puts that game straight back into the scanner's selection, and
-- `HousePointsService` -- which ticks every 30 seconds -- re-scores it. Silently: `addPoints` is an `INSERT IGNORE` and
-- the tick logs a count, not a diff.
--
-- Three conditions have to hold at once for a deleted row to come back, and all three usually do:
--
--   * the game is still in `house_games`, i.e. played within CleanService's last 32 days;
--   * it is inside the season window;
--   * the player still has a `house_members` row with `joined <= g.date`.
--
-- Deleting the membership **first** breaks the JOIN, drops the game from the selection entirely, and is what makes the
-- deletion stick. It is also the correct end state for a banned player, and exactly what `CleanService` does on its own
-- once Discord confirms the departure -- so it may already have happened, in which case that DELETE is a no-op and the
-- points can be removed unconditionally.
--
-- Two cases where the trap does not bite, worth knowing so as not to over-worry:
--
--   * The opponent is a house member too. The game then has two register rows; deleting one leaves `p.gold_id IS NOT
--     NULL`, and the game is never re-selected. Permanently safe on its own.
--   * The game is older than 32 days. It has left `ogs_games`/`kgs_games` and therefore `house_games`, and cannot be
--     re-selected whatever the membership says.
--
-- ⚠ Timing note for the season that opens on 1 September: for the first month of a season, essentially every row in the
-- register is inside the 32-day window, so the trap is live for the whole current season. Assume it bites.
--
-- HOW TO RUN IT
--
-- Against fg_prod. A dev run reads fg_dev, which is a stale snapshot of prod -- fine for rehearsing the statements,
-- useless as the target. Check which one you are on before anything else, since only `config.properties` is read and the
-- file it was copied from is invisible at runtime.
--
--   mysqldump -u <db.user> -p fg_prod house_points house_members > house_backup_$(date +%F).sql
--   mysql -u <db.user> -p fg_prod
--
-- There is no undo. Take the dump.
--
-- Not re-runnable in the useful sense -- it is a one-off, and a second run finds nothing to delete. It is however safe
-- to re-run: every statement is a DELETE on rows that are already gone.

-- ------------------------------------------------------------------------------------------------------------------
-- 1. The player, and the season
--
-- Two variables, set once. The season is the '<start>-<end>' string HouseSeason computes in Kotlin: a season runs from
-- 1 September to 31 May, so it is named after the September it opened in.

SET @player := '<discord_id>';
SET @season := '2026-2027';

-- Finding the id, if all you have is a pseudonym.
SELECT `discord_id`, `discord_name`, `left_server_since`
  FROM `discord_user_info`
 WHERE `discord_name` LIKE '%<pseudo>%';

-- If that returns nothing, CleanService has already purged the Discord row -- it does so a day after the departure is
-- confirmed -- and the name is gone with it. The points are not, so find them as orphans instead. On a healthy register
-- this returns only players who have left, which is a short list and doubles as a check that the id below is the right
-- one.
SELECT `p`.`discord_id`, `h`.`name` AS `house`, COUNT(*) AS `games`, SUM(`p`.`total`) AS `points`
  FROM `house_points` AS `p`
  JOIN `houses` AS `h` ON `h`.`id` = `p`.`house_id`
  LEFT JOIN `discord_user_info` AS `d` ON `d`.`discord_id` = `p`.`discord_id`
 WHERE `d`.`discord_id` IS NULL
   AND `p`.`season` = @season
 GROUP BY `p`.`discord_id`, `h`.`name`;

-- ------------------------------------------------------------------------------------------------------------------
-- 2. Look before deleting
--
-- `total` is the figure to read, never the sum of the seven bonus columns: below 19x19 a game credits less than its
-- breakdown, and `total` is what everything that ranks, sums or prints reads.

-- What is about to be removed, and from which house. All seasons, so the scope of the DELETE below is a deliberate
-- choice rather than the first thing that came out.
SELECT `h`.`name`, `p`.`season`, COUNT(*) AS `games`, SUM(`p`.`total`) AS `points`
  FROM `house_points` AS `p`
  JOIN `houses` AS `h` ON `h`.`id` = `p`.`house_id`
 WHERE `p`.`discord_id` = @player
 GROUP BY `h`.`name`, `p`.`season`
 ORDER BY `p`.`season`, `h`.`name`;

-- The standings as they stand. Keep this output: it is what step 4 is compared against.
SELECT `h`.`name`, SUM(`p`.`total`) AS `points`
  FROM `house_points` AS `p`
  JOIN `houses` AS `h` ON `h`.`id` = `p`.`house_id`
 WHERE `p`.`season` = @season
 GROUP BY `h`.`name`
 ORDER BY `points` DESC;

-- Is the membership still there? Empty means CleanService got there first and the trap is already defused.
SELECT * FROM `house_members` WHERE `discord_id` = @player;

-- How many of the doomed rows are re-scorable: the game still in the view, and no other player's row to hold the game
-- out of the selection. These are the ones the ordering below exists for. Zero means the DELETEs are safe in any order.
SELECT COUNT(*) AS `at_risk`
  FROM `house_points` AS `p`
  JOIN `house_games` AS `g` ON `g`.`gold_id` = `p`.`gold_id`
 WHERE `p`.`discord_id` = @player
   AND `p`.`season` = @season
   AND NOT EXISTS (
     SELECT 1 FROM `house_points` AS `o`
      WHERE `o`.`gold_id` = `p`.`gold_id` AND `o`.`discord_id` <> @player
   );

-- ------------------------------------------------------------------------------------------------------------------
-- 3. The deletion
--
-- One transaction, membership first. Both matter: the scanner ticks every 30 seconds, and points deleted while the
-- membership still stands can be rewritten before the second statement lands.
--
-- The season filter on the second DELETE is the scope. Drop it to remove every season -- but read the note at the foot
-- of this file first.

START TRANSACTION;

DELETE FROM `house_members`
 WHERE `discord_id` = @player;

DELETE FROM `house_points`
 WHERE `discord_id` = @player
   AND `season` = @season;

-- Read the two row counts against step 2 before committing. ROLLBACK if they disagree.
COMMIT;

-- ------------------------------------------------------------------------------------------------------------------
-- 4. Verification
--
-- The first two must return nothing. The third must show the player's former house lower by exactly the figure step 2
-- reported for that house and season.

SELECT * FROM `house_points` WHERE `discord_id` = @player AND `season` = @season;
SELECT * FROM `house_members` WHERE `discord_id` = @player;

SELECT `h`.`name`, SUM(`p`.`total`) AS `points`
  FROM `house_points` AS `p`
  JOIN `houses` AS `h` ON `h`.`id` = `p`.`house_id`
 WHERE `p`.`season` = @season
 GROUP BY `h`.`name`
 ORDER BY `points` DESC;

-- Then wait three minutes -- several HousePointsService ticks -- and run the first query again. It must STILL return
-- nothing. Rows reappearing means the membership deletion did not take while the games are still inside the 32-day
-- window; check `house_members` and repeat step 3.
--
-- Last, read the standings off the running app rather than off the database, so that the API and the website are seen
-- to agree: `GET /gold/api/house/standings` on `gold.api.port`, or the house page on `frontend.url`.

-- ------------------------------------------------------------------------------------------------------------------
-- SCOPE NOTES, for the next time this is needed
--
-- * Only rows carrying the current `season` affect the current standings -- `houseTotals` filters on it -- so deleting
--   the current season is enough to correct what is on screen today. Removing older seasons rewrites history, and the
--   houses those points went to lose them too.
-- * `total = 0` on an old row is not a row worth nothing; it is the mark of a row written by a jar predating
--   `migration taille de goban.sql`. Sums over old seasons can therefore read low. See that file's header.
-- * `house_points`.`house_id` is frozen at write time, so a player who changed house is credited to the house they were
--   in when they played. `@player` catches all of them; a per-house DELETE would need `AND house_id = ...`.
-- * To also remove the games -- so they stop counting for FGC validity and leave the API -- delete from `ogs_games` /
--   `kgs_games` by `gold_id`, which is what `removeAnnulledGames` does, and do it BEFORE the points, for the same
--   reason the membership goes first. FGC self-corrects on its next tick because `FgcService` overwrites its counts
--   from the view rather than incrementing them.
-- * If this stops being a one-off, the right shape is a method on `HouseDatabaseAccessor` mirroring
--   `removeAnnulledGames`, not this file run again from memory.
