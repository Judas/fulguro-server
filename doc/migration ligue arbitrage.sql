-- League: an administrator can rule on a match. Four nullable columns on `league_matches`, nothing else.
--
-- A ruling gives each side one award, chosen freely, at most one WINNER per match:
--
--   * FORFEIT     -- 0 renown, the session is lost for the perfect-attendance bonus (what `unplayed` does today).
--   * EXEMPT      -- 0 renown, the session counts as exempted, so the bonus is kept.
--   * PARTICIPANT -- counts as played: 2 renown.
--   * WINNER      -- counts as played and won: 7 renown.
--
-- The ruling OVERLAYS `result`, it does not replace it. The OGS sweep and the settlement keep writing `result` as
-- before; the standings read the awards first when they are set. Clearing the four columns hands the match back to
-- whatever `result` says, which is what makes a ruling undoable. Both awards are NULL or both are set -- the accessor
-- only ever writes them together.
--
-- Renown only: house points and FGC come from `ogs_games`, not from this table, so nothing else needs touching.
--
-- APPLY THIS **BEFORE** DEPLOYING THE JAR:
--
--   * Additive for the running jar. Every read of `league_matches` is a `SELECT *` mapped with
--     `throwOnMappingFailure(false)`, so the old jar drops the new columns rather than failing, and it never writes them.
--   * The new jar needs them: its standings query names `black_award` / `white_award`, and fails without them.
--
-- Apply on `fg_dev` first, then on `fg_prod`. Idempotence: MySQL has no `ADD COLUMN IF NOT EXISTS`, so a second run
-- fails on "Duplicate column name" and changes nothing.

ALTER TABLE `league_matches`
  ADD COLUMN `black_award`    VARCHAR(16)  NULL AFTER `result`,
  ADD COLUMN `white_award`    VARCHAR(16)  NULL AFTER `black_award`,
  ADD COLUMN `adjudicated`    DATETIME     NULL AFTER `white_award`,
  ADD COLUMN `adjudicated_by` VARCHAR(255) NULL AFTER `adjudicated`;

-- Check:
--   SHOW COLUMNS FROM `league_matches` LIKE '%award%';
--   SELECT season, session, black_discord_id, black_award, white_award, adjudicated, adjudicated_by
--     FROM `league_matches` WHERE adjudicated IS NOT NULL;
