package com.fulgurogo.house

import com.fulgurogo.common.config.Config
import com.fulgurogo.common.logger.log
import com.fulgurogo.discord.DiscordModule
import com.fulgurogo.house.HouseModule.TAG
import com.fulgurogo.discord.db.DiscordDatabaseAccessor
import com.fulgurogo.house.db.model.House
import com.fulgurogo.house.db.model.HouseRankedMember
import com.fulgurogo.house.db.model.HouseStanding
import com.fulgurogo.house.db.model.ranked

/**
 * The three things the bot says about the houses: someone arrived, here is today's standing, here is how the season
 * ended. Nothing else — the scanner is silent by design, or every blitz game would be announced twice over.
 *
 * All three go to `bot.notification.channel.id`, the channel games are already announced in, so there is no new config
 * key. French throughout, because this is the one part of the module a player reads.
 *
 * Formatting only. *When* to say these things lives in the callers — [HouseSeasonService] for the two dated ones, the
 * `join` handler for an arrival — because the guards that make them happen once are rows in `house_seasons`, and a
 * formatter that reached for the database to decide whether to speak would be much harder to reason about.
 */
object HouseNotifier {
    private const val EMOJI = ":shield:"

    /**
     * ⚠ Assumed, not agreed: the website lives in another repository and this path is not part of any contract yet. A
     * wrong guess is a dead link in every ranking message, so it is worth one check against the site's routes.
     */
    private const val HOUSES_PATH = "/maisons"

    /**
     * A player joining a house, from whichever of the two paths put them there: `POST /gold/api/house/join` during the
     * season, or a `CHANGE` intention applied when a season opens.
     *
     * One function for both on purpose. Two call sites writing their own wording is how the same event ends up
     * described two different ways depending on the month it happened in.
     */
    fun notifyArrival(discordId: String, house: House) = send(
        title = "$EMOJI Une nouvelle recrue chez les ${house.name} !",
        message = "**${nameOf(discordId)}** rejoint les **${house.name}**.\n*${house.tagline}*"
    )

    /** Today's standing: the four houses in order, each with its best current member. */
    fun notifyDailyRanking(season: String, standings: List<HouseStanding>) = send(
        title = "$EMOJI Classement des Maisons",
        message = standings.ranked().joinToString("\n") { line(it) } + "\n\n" + fullRankingLink()
    )

    /**
     * The end-of-season recap: who won, the final standing, and the best player of each house.
     *
     * The winner is read off the totals rather than off the first line, so a genuine tie is announced as a tie instead of
     * quietly crowning whichever house sorted first. With the headcount tiebreak in [ranked] a tie needs equal points
     * *and* equal size, which is unlikely — and exactly why it would never be noticed if it were wrong.
     */
    fun notifySeasonRecap(season: String, standings: List<HouseStanding>) {
        val ordered = standings.ranked()
        val best = ordered.maxOfOrNull { it.totalPoints } ?: 0
        val winners = ordered.filter { it.totalPoints == best }

        val headline = when {
            best == 0 -> "Aucune maison n'a marqué cette saison."
            winners.size == 1 -> "Les **${winners.first().house.name}** remportent la saison $season !"
            else -> "La saison $season se termine sur une égalité entre " +
                    winners.joinToString(" et ") { "les **${it.house.name}**" } + " !"
        }

        send(
            title = "$EMOJI Fin de la saison $season",
            message = headline + "\n\n" + ordered.joinToString("\n") { line(it) } + "\n\n" + fullRankingLink()
        )
    }

    /**
     * One house's line: its name, its total, its size and its best member.
     *
     * Deliberately unnumbered. The order carries the ranking, and a numeral counted off the list would print a 2nd and a
     * 3rd where two houses are tied — the same reason `/gold/api/houses` attaches no rank to a house.
     */
    private fun line(standing: HouseStanding): String {
        val head = "**${standing.house.name}** — ${points(standing.totalPoints)} " +
                "*(${standing.memberCount} ${if (standing.memberCount > 1) "membres" else "membre"})*"
        return standing.leader?.let { "$head\n${leaderLine(it)}" } ?: head
    }

    /**
     * A house with nobody in it has no leader and gets no line at all, rather than an empty one.
     *
     * Prefixed with a character rather than indented with spaces. Leading whitespace in an embed is at the mercy of how
     * the client collapses it, and it is the one part of these messages the server cannot check for itself.
     */
    private fun leaderLine(leader: HouseRankedMember): String =
        "↳ En tête : ${leader.discordName ?: "?"} *(${points(leader.total())})*"

    private fun points(total: Int): String = "**$total point${if (total > 1) "s" else ""}**"

    private fun fullRankingLink(): String = "[Classement complet](${Config.get("frontend.url")}$HOUSES_PATH)"

    /**
     * The player's Discord name, falling back to their id.
     *
     * Read from `discord_user_info` rather than from JDA's cache: the cache can miss, and this table is what every other
     * read in the app already trusts for a name. The id is a poor thing to show a channel, but it is better than a
     * message with a hole in it, and it only happens for a member the bot has never profiled.
     */
    private fun nameOf(discordId: String): String =
        DiscordDatabaseAccessor.user(discordId)?.discordName ?: discordId

    /**
     * One log line per announcement, because JDA's `queue()` reports nothing back: without this, whether the
     * once-a-year recap actually went out is unanswerable after the fact. The title alone — the body is in the channel.
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
