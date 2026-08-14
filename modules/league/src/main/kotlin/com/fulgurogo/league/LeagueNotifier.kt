package com.fulgurogo.league

import com.fulgurogo.common.config.Config
import com.fulgurogo.common.logger.log
import com.fulgurogo.discord.DiscordModule
import com.fulgurogo.discord.db.DiscordDatabaseAccessor
import com.fulgurogo.house.db.model.House
import com.fulgurogo.league.LeagueModule.TAG
import com.fulgurogo.league.db.model.LeagueAcademyStanding
import com.fulgurogo.league.db.model.LeagueMatch
import com.fulgurogo.league.db.model.LeagueSide
import com.fulgurogo.league.db.model.LeagueStanding
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Everything the bot says about the league: a player's challenge by DM, the draw and the closing recap on the channel.
 *
 * Formatting only, like `HouseNotifier`. The *when* stays in [LeagueSessionService], because the guards that make these
 * happen once are rows of `league_sessions` and `league_matches`, and a formatter that read the database to decide whether
 * to speak would be much harder to reason about. French throughout — this is the part of the module players read.
 *
 * All three go to `bot.notification.channel.id` or to a DM, so there is no new config key and no channel to create on
 * either guild. Seventeen channel messages a season, in a channel that already announces every game played on KGS and
 * OGS: cheap, at the price of being lost in that flow. An optional key falling back to this one can be added later
 * without breaking anything.
 *
 * ⚠⚠ **[notifyChallenge] is the delivery mechanism, not a courtesy**, and it is the one thing in this module that cannot
 * repair itself. Creating a match at OGS notifies nobody — no notification, no invitation, no list the player would find
 * it in — so the invitation link reaching them by DM is the only way they learn they have a match. A DM that never
 * arrives is an unplayable game, which is why the caller records "notified" from inside Discord's success callback, and
 * why the links are never deleted: resending one by hand is a real repair.
 */
object LeagueNotifier {
    /**
     * The league's page on the website, appended to `frontend.url` — as the houses do with `/houses`.
     *
     * One constant, here, because the announcements are the only thing that links to it and nothing else on the server
     * depends on the site's routing. The same path serves the API, `/gold/api/league`, so the two cannot drift.
     */
    const val LEAGUE_PATH = "/league"

    // TODO Emoji update when ready
    private const val EMOJI = ":crossed_swords:"

    /**
     * `jeudi 30 avril à 23h59` — the last moment a game may still be *started*. See [notifyChallenge].
     *
     * ⚠ **[Locale.FRENCH] explicitly, never the JVM default.** Measured: the default here is `en_US`, which rendered the
     * one message players actually read as `Thursday 30 April à 23h59` — an English weekday inside a French sentence. The
     * production server's locale is not ours to assume either way, and this is the only date the module formats.
     */
    private val DEADLINE = DateTimeFormatter.ofPattern("EEEE d MMMM 'à' HH'h'mm", Locale.FRENCH)

    /**
     * One player's challenge, by DM: who they play, on which side, until when, and the link.
     *
     * The deadline is in the message because it is the one thing a player cannot work out for themselves and the one that
     * costs them if missed — the game has to be **started** before the session ends, and afterwards the match is worth
     * nothing however well it is played. The session's `end` is exclusive, so it is shown as the last minute before it:
     * telling somebody their deadline is "1 June" when the truth is "31 May at 23:59" invites exactly the mistake this
     * line exists to prevent.
     *
     * [onSuccess] is called only if Discord accepted the message, and it is what stamps the row. Passing it down rather
     * than answering a boolean is the whole design: `queue()` is asynchronous, so a synchronous answer could only ever
     * have meant "queued", never "delivered".
     *
     * The link goes out **only** here. It is a player secret: it never reaches a channel, and never reaches the API.
     */
    fun notifyChallenge(match: LeagueMatch, side: LeagueSide, session: Session, onSuccess: () -> Unit) {
        val (me, opponent, invite) = when (side) {
            LeagueSide.BLACK -> Triple(match.blackDiscordId, match.whiteDiscordId, match.blackInvite)
            LeagueSide.WHITE -> Triple(match.whiteDiscordId, match.blackDiscordId, match.whiteInvite)
        }

        if (invite == null) {
            // Only reachable if a challenge was recorded without its links, which setMatchChallenge does not do. Saying so
            // beats sending a player a message with a hole where the link should be.
            log(TAG, "notifyChallenge session ${match.session} $me FAILURE no invitation link stored")
            return
        }

        val colour = if (side == LeagueSide.BLACK) "**Noir**" else "**Blanc**"
        val deadline = DEADLINE.format(session.end.minusMinutes(1))

        log(TAG, "notifyChallenge session ${match.session} $me as $side")
        DiscordModule.discordBot.sendPrivateMessageEmbeds(
            discordId = me,
            title = "$EMOJI Ligue d'Aurak — Session ${match.session}",
            message = "Ton adversaire est **${nameOf(opponent)}**, et tu joues $colour.\n\n" +
                    "[▶ Rejoindre la partie]($invite)\n\n" +
                    "⏳ La partie doit être **lancée** avant le $deadline. Passé ce délai, elle ne compte plus pour la " +
                    "ligue.\n\n" +
                    "🔒 Ce lien n'est valable que pour toi : ne le partage pas.\n\n" +
                    fullStandingsLink(),
            onSuccess = onSuccess
        )
    }

    /**
     * The draw, on the channel: how many matches, how many were benched, and a link to the site.
     *
     * Deliberately **not** the list of pairings. At twenty matches the embed is unreadable, and Discord caps a description
     * at 2 048 characters — beyond which the whole message is refused rather than trimmed, so the failure would be total.
     * The site has the table.
     */
    fun notifyDraw(season: String, session: Int, matches: Int, exempted: Int) {
        val benched = when (exempted) {
            0 -> ""
            1 -> "\n\nUn joueur n'a pas pu être apparié cette session."
            else -> "\n\n$exempted joueurs n'ont pas pu être appariés cette session."
        }

        send(
            title = "$EMOJI Ligue d'Aurak — Session $session",
            message = "Les appariements de la session $session sont prêts : " +
                    "**$matches ${if (matches > 1) "matchs" else "match"}**.\n" +
                    "Chaque joueur reçoit son lien de partie en message privé." + benched + "\n\n" +
                    fullStandingsLink()
        )
    }

    /**
     * The end of a season: who won, how the academies finished, and the best player of each.
     *
     * The winner is read off the totals rather than off the first line, as the houses' recap does, so a genuine tie is
     * announced as one instead of quietly crowning whoever sorted first.
     *
     * ⚠ An academy's renown and the sum of its members' renown are **not** the same figure, and both are right. The
     * academy's is counted over the house frozen on each match, so it survives a player leaving, while the
     * perfect-attendance bonus is a personal distinction that belongs to no house. See [LeagueAcademyStanding].
     */
    fun notifySeasonRecap(
        season: String,
        standings: List<LeagueStanding>,
        academies: List<LeagueAcademyStanding>,
        houses: List<House>
    ) {
        val best = standings.maxOfOrNull { it.renown.total() } ?: 0
        val champions = standings.filter { it.renown.total() == best && best > 0 }

        val headline = when {
            champions.isEmpty() -> "Personne n'a marqué de renommée cette saison."
            champions.size == 1 -> "**${nameOf(champions.first().discordId)}** remporte la Ligue d'Aurak $season " +
                    "avec ${renown(best)} !"

            else -> "La Ligue d'Aurak $season se termine sur une égalité entre " +
                    champions.joinToString(" et ") { "**${nameOf(it.discordId)}**" } + ", à ${renown(best)} !"
        }

        val byHouse = academies.associateBy { it.houseId }
        val members = standings.filter { it.houseId != null }.groupBy { it.houseId }
        val table = houses
            .sortedByDescending { byHouse[it.id]?.renown() ?: 0 }
            .joinToString("\n\n") { house -> academyLine(house, byHouse[house.id], members[house.id].orEmpty()) }

        send(
            title = "$EMOJI Fin de la Ligue d'Aurak — Saison $season",
            message = "$headline\n\n$table\n\n" + fullStandingsLink()
        )
    }

    /**
     * One academy's line: its renown, and its best player.
     *
     * An academy with no match still gets a line, at zero. Dropping it would make a house that took part and lost look
     * like one that does not exist, and the four academies are a fixed cast the reader expects to see in full.
     */
    private fun academyLine(house: House, academy: LeagueAcademyStanding?, members: List<LeagueStanding>): String {
        val head = "**${house.name}** — ${renown(academy?.renown() ?: 0)}"
        val leader = members.maxByOrNull { it.renown.total() } ?: return head
        return "$head\n↳ Meilleur joueur : ${leader.discordName ?: leader.discordId} " +
                "*(${renown(leader.renown.total())})*"
    }

    private fun renown(total: Int): String = "**$total point${if (total > 1) "s" else ""} de renommée**"

    private fun fullStandingsLink(): String = "[Classement de la ligue](${Config.get("frontend.url")}$LEAGUE_PATH)"

    /**
     * The player's Discord name, falling back to their id.
     *
     * Read from `discord_user_info` rather than from JDA's cache, which can miss — the same choice `HouseNotifier` makes,
     * and this table is what every other read in the app already trusts for a name.
     */
    private fun nameOf(discordId: String): String =
        DiscordDatabaseAccessor.user(discordId)?.discordName ?: discordId

    /**
     * One log line per channel announcement, because `queue()` reports nothing back: without it, whether the once-a-year
     * recap actually went out is unanswerable afterwards. The title alone — the body is in the channel.
     */
    private fun send(title: String, message: String) {
        log(TAG, "Announcing [$title]")
        DiscordModule.discordBot.sendMessageEmbeds(
            channelId = Config.get("bot.notification.channel.id"),
            message = message,
            title = title
        )
    }
}
