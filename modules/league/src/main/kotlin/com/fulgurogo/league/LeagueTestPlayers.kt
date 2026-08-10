package com.fulgurogo.league

import com.fulgurogo.common.config.Config
import com.fulgurogo.common.logger.log
import com.fulgurogo.league.LeagueModule.TAG

/**
 * The dev sandbox, and **the league's only guard against production**.
 *
 * Every other part of this application is isolated by config: `db.name`, `bot.token`, `bot.guild.id` and
 * `bot.notification.channel.id` all differ between dev and prod, so a local `./gradlew :app:run` cannot by construction
 * touch anything live. The OGS league does not have that protection — there is one league, `FulguroGo`, and dev and prod
 * share it. A local run therefore creates real matches on the real league and sends real invitation links.
 *
 * What bounds the damage is this list, so it is worth reading as a safety device rather than as a convenience:
 *
 * - **Non-empty** — only these Discord ids may enter the league. In dev that is the two test accounts.
 * - **Empty** — no restriction at all. That is the production setting.
 *
 * The league's *writes* still land in `fg_dev` when run locally, so a local draw cannot move the production standings.
 * What leaks to production is the OGS side: matches and links in the real league, for the accounts listed here.
 *
 * The startup log line is what makes the two mirror mistakes visible, and they are equally bad: left filled in
 * production it would silently cut the league down to two players, left empty in dev a local run would pair the whole
 * community on the real league. In dev the *absence* of the line is itself the warning.
 */
object LeagueTestPlayers {
    private const val KEY = "league.test.players"

    /** Empty means "no restriction", which is what production wants. */
    val ids: List<String> = read()

    /** Whether the sandbox is closed, i.e. whether the list restricts anything. */
    fun isActive(): Boolean = ids.isNotEmpty()

    /** Everybody is allowed when the list is empty — that is the production case, not an oversight. */
    fun isAllowed(discordId: String): Boolean = ids.isEmpty() || discordId in ids

    /**
     * One line at startup, and only when the list restricts something.
     *
     * Called from [LeagueModule.init] so it lands at startup rather than on the first join, which is the whole point: a
     * sandbox nobody notices is worse than no sandbox.
     */
    fun logState() {
        if (isActive()) log(TAG, "$KEY is set: only ${ids.size} account(s) may enter the league")
    }

    /**
     * Read through [Config.getOrNull] and allowed to be absent, like `house.period.override` — the only other optional
     * key in the project. Blank entries are dropped so a trailing comma cannot create an id nobody can ever match.
     */
    private fun read(): List<String> = Config.getOrNull(KEY)
        ?.split(",")
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?: listOf()
}
