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
    private const val EMOJI = ":homes:"
    private const val EMOJI_KEY_PREFIX = "house.emoji."
    private const val CREST_BASE_OVERRIDE_KEY = "house.crest.base.override"
    private const val HOUSES_PATH = "/houses"
    private const val CRESTS_PATH = "/crests"
    private const val CREST_SUFFIX = "_BG.png"

    /**
     * A player joining a house, from whichever of the two paths put them there: `POST /gold/api/house/join` during the
     * season, or a `CHANGE` intention applied when a season opens.
     *
     * One function for both on purpose. Two call sites writing their own wording is how the same event ends up
     * described two different ways depending on the month it happened in.
     *
     * The only one of the three dressed in the colours of a single house — its own emoji instead of the generic one,
     * its crest as the embed thumbnail. The other two are about the four houses at once and have nobody's colours to
     * wear.
     */
    fun notifyArrival(discordId: String, house: House) = send(
        title = "**${nameOf(discordId)}** rejoint la maison **${house.name}** !",
        message = "${emojiOf(house)} *${house.tagline}*",
        imageUrl = crestOf(house)
    )

    /** Today's standing: the four houses in order, each with its best current member. */
    fun notifyDailyRanking(season: String, standings: List<HouseStanding>) = send(
        title = "$EMOJI Classement des Maisons",
        message = standings.ranked().joinToString("\n\n") { line(it) } + "\n\n" + fullRankingLink()
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
            winners.size == 1 -> "La maison **${winners.first().house.name}** remporte la saison $season !"
            else -> "La saison $season se termine sur une égalité entre les maisons " +
                    winners.joinToString(" et ") { "**${it.house.name}**" } + " !"
        }

        send(
            title = "$EMOJI Fin de la saison $season",
            message = headline + "\n\n" + ordered.joinToString("\n\n") { line(it) } + "\n\n" + fullRankingLink()
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
        "↳ Leader : ${leader.discordName ?: "?"} *(${points(leader.total)})*"

    private fun points(total: Int): String = "**$total point${if (total > 1) "s" else ""}**"

    /**
     * A house's own Discord emoji, falling back to the generic [EMOJI] when it has none configured.
     *
     * Config and not a constant, for the reason the roles next door are config: the value is a custom emoji tag,
     * `<:name:id>`, and that id only means anything on the guild that owns the emoji. The dev and prod files already
     * name different guilds, so the same four crests uploaded to both have two different sets of ids — a hardcoded tag
     * would render as raw text on whichever guild it was not taken from.
     *
     * Unlike [HouseRoles], a missing value is not logged. The fallback is right there in the channel for anyone to see,
     * and prod legitimately runs without these until someone fills them in.
     */
    private fun emojiOf(house: House): String =
        Config.getOrNull("$EMOJI_KEY_PREFIX${house.slug.lowercase()}")?.trim()?.takeIf { it.isNotEmpty() } ?: EMOJI

    /**
     * The house's crest, shown as the embed thumbnail. Built from the slug, the way the website builds it.
     *
     * No per-house config: the crests are website assets, so the slug and the site's address are all it takes — the
     * same two things [fullRankingLink] already puts together. The four houses could only ever be four keys saying the
     * same thing four times, and a fifth house would then need a deploy to get a picture.
     *
     * `_BG.png` and not the `.svg` the site itself shows, for two reasons that both come from Discord: it renders no
     * SVG in an embed at all, and the plain crest is transparent, which turns half of it invisible against a dark
     * client. `_BG` is the version with the background baked in.
     *
     * The base is overridable because dev cannot use its own: `frontend.url` is `localhost` there, and Discord fetches
     * the image from its own servers, so a local run would post announcements with no thumbnail and nobody could ever
     * check what one looks like before it ships. The override points dev at the deployed site's images — read-only,
     * and the same four files prod would serve.
     */
    private fun crestOf(house: House): String {
        val base = Config.getOrNull(CREST_BASE_OVERRIDE_KEY)?.trim()?.takeIf { it.isNotEmpty() }
            ?: Config.get("frontend.url")
        return "$base$CRESTS_PATH/${house.slug}$CREST_SUFFIX"
    }

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
    private fun send(title: String, message: String, imageUrl: String = "") {
        log(TAG, "Announcing [$title]")
        DiscordModule.discordBot.sendMessageEmbeds(
            channelId = Config.get("bot.notification.channel.id"),
            message = message,
            title = title,
            imageUrl = imageUrl
        )
    }
}
