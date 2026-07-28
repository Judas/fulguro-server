-- Removal of the FOX and IGS servers and of the FFG and EGF federations, part 2 of 2.
--
-- APPLY THIS ONLY ONCE THE JAR WITHOUT THOSE MODULES IS RUNNING, and once part 1 has rewritten the views.
--
--   * under the old jar, FoxService, IgsService, FfgService, EgfService and CleanService all read these tables.
--     Their stalest() runs outside StalestFirstService's try, so a missing table propagates to PeriodicFlowService,
--     no tick ever succeeds again, and GET /gold/api/health answers 503 until the new jar lands;
--   * if the views of part 1 have not been applied, dropping these tables leaves gold_ranks, api_players and
--     api_games pointing at tables that no longer exist. MySQL does not validate a view body at DDL time, so the
--     drops succeed and every read of those views then fails with ER_VIEW_INVALID (1356) -- a 500 on every API route,
--     while GoldService quietly stamps error = 1 on all 297 gold_ratings rows and still reports itself healthy.
--
-- This is the point of no return for the jar: 8.7 cannot run once these tables are gone. Back them up first, even
-- though four of them hold at most 17 rows and fox_games is empty:
--
--   mysqldump -u <db.user> -p fg_prod \
--     ffg_user_info egf_user_info igs_user_info fox_user_info fox_games > rollback-tables.sql
--
--   mysql -u <db.user> -p fg_prod < "doc/migration remove servers - 2 after deploy.sql"
--
-- Every statement is idempotent, so the file can be re-run.
--
-- No foreign key, trigger, procedure or secondary index references these tables, and no remaining table has a column
-- naming one of these platforms, so the order below does not matter -- only that part 1 came first.

DROP TABLE IF EXISTS `fox_games`;
DROP TABLE IF EXISTS `fox_user_info`;
DROP TABLE IF EXISTS `igs_user_info`;
DROP TABLE IF EXISTS `ffg_user_info`;
DROP TABLE IF EXISTS `egf_user_info`;

-- Checks. The first should list 9 tables and 4 views, the others should still answer without error 1356.
--
--   SHOW FULL TABLES;
--   SELECT COUNT(*) FROM `gold_ranks`;
--   SELECT COUNT(*) FROM `api_players`;
--   SELECT COUNT(*) FROM `api_games`;
