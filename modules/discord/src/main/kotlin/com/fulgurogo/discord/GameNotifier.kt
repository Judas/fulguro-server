package com.fulgurogo.discord

import com.fulgurogo.common.config.Config
import com.fulgurogo.common.utilities.DATE_ZONE
import com.fulgurogo.common.utilities.toDate
import java.time.ZonedDateTime
import java.util.*

/** A game that can be announced on Discord. Implemented by each platform's stored game model. */
interface NotifiableGame {
    val date: Date
    fun isFinished(): Boolean
    fun description(): String
}

/** Announces games in the Discord notification channel. Shared by every platform that stores games. */
object GameNotifier {
    /** A game that started longer ago than this is stale news and is not announced. */
    private const val MAX_AGE_HOURS = 4L

    /**
     * @param server platform name shown in the title, e.g. `"OGS"`.
     * @param checkAge whether to drop games that started more than [MAX_AGE_HOURS] ago. `OgsRealTimeService` passes
     *   false because it only ever sees games from the live game list, where the check could only make it miss one.
     */
    fun notify(game: NotifiableGame, server: String, checkAge: Boolean = true) {
        if (checkAge && ZonedDateTime.now(DATE_ZONE).minusHours(MAX_AGE_HOURS).toDate().after(game.date)) return

        DiscordModule.discordBot.sendMessageEmbeds(
            channelId = Config.get("bot.notification.channel.id"),
            message = game.description(),
            title = ":popcorn: Partie ${if (game.isFinished()) "terminée" else "en cours"} sur $server !",
            imageUrl = if (game.isFinished()) "" else Config.get("gold.ongoing.game.thumbnail")
        )
    }
}
