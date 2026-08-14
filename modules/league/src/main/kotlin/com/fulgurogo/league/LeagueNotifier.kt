package com.fulgurogo.league

import com.fulgurogo.common.logger.log
import com.fulgurogo.league.LeagueModule.TAG
import com.fulgurogo.league.db.model.LeagueMatch
import com.fulgurogo.league.db.model.LeagueSide
import com.fulgurogo.league.db.model.LeagueStanding

/**
 * Everything the league says on Discord. **Step 10 of `doc/plan-ligue.md` fills these in; for now they only log.**
 *
 * It exists already, as a stub, because the session service is finished and has to call something. The alternative was
 * commented-out calls in the service, which is worse: a seam that compiles and logs is one that can be tested and cannot
 * be forgotten, and it keeps the service's shape final so step 10 changes this file and nothing else.
 *
 * What each of them owes, when written: [notifyChallenge] is a **private message** carrying an invitation link, which is
 * a player secret and must never go to a channel; the other two are channel messages on `bot.notification.channel.id`,
 * the houses' channel.
 *
 * ⚠⚠ **[notifyChallenge] is the delivery mechanism, not a courtesy.** Established by use on 11 August 2026: creating a
 * match at OGS notifies nobody — no notification, no invitation, no list the player would find it in. The invitation link
 * is the only way a player learns they have a match. So a DM that fails is a match that **cannot be played**, which is
 * why the caller stamps `*_notified` only on success and why resending by hand is a real repair rather than a nicety.
 */
object LeagueNotifier {
    /**
     * The league's page on the website, appended to `frontend.url` — as the houses do with `/houses`.
     *
     * One constant, here, because the announcements are the only thing that links to it and nothing else on the server
     * depends on the site's routing. The same path serves the API, `/gold/api/league`, so the two cannot drift.
     */
    const val LEAGUE_PATH = "/league"

    /**
     * Sends one player their invitation link, by DM.
     *
     * The caller stamps `black_notified` / `white_notified` only when this succeeds, so a failure is retried on the next
     * tick and a link is never recorded as sent when it was not.
     */
    fun notifyChallenge(match: LeagueMatch, side: LeagueSide): Boolean {
        val link = when (side) {
            LeagueSide.BLACK -> match.blackInvite
            LeagueSide.WHITE -> match.whiteInvite
        }
        // The link itself is deliberately absent from this line: it is a secret, and a log is not a DM.
        log(
            TAG,
            "notifyChallenge STUB (step 10) session ${match.session} ${match.playerOn(side)} as $side," +
                    " link ${if (link == null) "missing" else "ready"}"
        )
        return false
    }

    /** Announces a session's pairings on the channel. */
    fun notifyDraw(season: String, session: Int, matches: List<LeagueMatch>, exempted: List<String>) {
        log(
            TAG,
            "notifyDraw STUB (step 10) $season session $session: ${matches.size} match(es)," +
                    " ${exempted.size} exempted"
        )
    }

    /** Announces the final standings of a season on the channel. */
    fun notifySeasonRecap(season: String, standings: List<LeagueStanding>) {
        log(
            TAG,
            "notifySeasonRecap STUB (step 10) $season: " +
                    standings.take(3).joinToString { "${it.discordName ?: it.discordId} ${it.renown.total()}" }
        )
    }
}
