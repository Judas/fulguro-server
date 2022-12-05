package com.fulgurogo.features.league

import com.fulgurogo.Config
import com.fulgurogo.features.database.DatabaseAccessor
import com.fulgurogo.features.games.GameScanListener
import com.fulgurogo.features.ladder.LadderPlayer
import com.fulgurogo.utilities.*
import com.fulgurogo.utilities.Logger.Level.INFO
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.JDA
import java.time.DayOfWeek
import java.time.ZonedDateTime
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class LeagueService(private val jda: JDA) : GameScanListener {
    override fun onScanStarted() {
        log(INFO, "onScanStarted")
    }

    override fun onScanFinished() {
        log(INFO, "onScanFinished")

        updateRushResults()

        val now = ZonedDateTime.now(DATE_ZONE)
        if (now.dayOfWeek == DayOfWeek.SUNDAY && now.hour in 20 until 22) drawPairings()
    }

    private fun updateRushResults() {
        log(INFO, "updateRushResults")

        val ladderGames = DatabaseAccessor.ladderGames().sortedBy { it.date }
        val rush = DatabaseAccessor.currentRush()

        // Get unplayed pairings
        DatabaseAccessor
            .leaguePairings(rush)
            .filterNot { it.exempt }
            .filter { it.winnerId == null }
            .forEach { pairing ->
                // Search for first ladder game between given players
                ladderGames
                    .filter { it.date.after(pairing.date) }
                    .firstOrNull { game -> pairing.containsPlayers(game.mainPlayerId, game.opponentId) }
                    ?.let { game ->
                        // Update game result
                        val winnerId = if (game.mainPlayerWon == true) game.mainPlayerId else game.opponentId
                        val loserId = if (game.mainPlayerWon == true) game.opponentId else game.mainPlayerId
                        DatabaseAccessor.updateLeaguePairings(pairing, game.id, winnerId)

                        val winnerLink = winnerId?.let { id ->
                            val user = DatabaseAccessor.ensureUser(id)
                            val player = DatabaseAccessor.ladderPlayer(user)
                            val rank = player?.rating?.toRank()?.rankToString(false) ?: "?"
                            val link = "${Config.Ladder.WEBSITE_URL}/players/$id"
                            "**[${user.name} ($rank)]($link)**"
                        }

                        val loserLink = loserId?.let { id ->
                            val user = DatabaseAccessor.ensureUser(id)
                            val player = DatabaseAccessor.ladderPlayer(user)
                            val rank = player?.rating?.toRank()?.rankToString(false) ?: "?"
                            val link = "${Config.Ladder.WEBSITE_URL}/players/$id"
                            "**[${user.name} ($rank)]($link)**"
                        } ?: ""

                        val gameLink = "${Config.Ladder.WEBSITE_URL}/players/${game.mainPlayerId}/game/${game.id}"
                        jda.publicMessage(
                            Config.League.CHANNEL_ID,
                            "$winnerLink gagne contre $loserLink !\n[Voir la partie]($gameLink)",
                            "Oteai Gold - Rush $rush"
                        )
                    }
            }
    }

    fun drawPairings() = CoroutineScope(Dispatchers.IO).launch {
        log(INFO, "drawPairings")

        val currentRush = DatabaseAccessor.currentRush()
        val rush = currentRush + 1
        val date = ZonedDateTime.now(DATE_ZONE).toDate()

        // Get all players with Gold role
        val rushPlayers = jda
            .getGuildById(Config.GUILD_ID)
            ?.findMembers { member ->
                member.roles.any { it.id == Config.League.ROLE }
            }
            ?.get()
            ?.mapNotNull { DatabaseAccessor.ladderPlayer(DatabaseAccessor.ensureUser(it.user)) }
            ?.toMutableList()
            ?: mutableListOf()

        log(INFO, "Found ${rushPlayers.size} players with the role")

        val previousPairings = DatabaseAccessor.leaguePairings(currentRush)
        val pairings = mutableListOf<Pair<LeaguePairing, Int>>()

        // If odd number of players, remove one randomly
        if (rushPlayers.size % 2 == 1) {
            val exempted = rushPlayers.random()
            rushPlayers.remove(exempted)
            log(INFO, "Exempting player ${exempted.discordId}")
            pairings.add(Pair(LeaguePairing.exempted(exempted, rush, date), Int.MAX_VALUE))
        }

        // We take each player 1-by-1 taking extremities first
        val playerWindow = 6 // Choose between the nearest 6 players
        val remainingPlayers = rushPlayers.toMutableList()
        sortByExtremes(rushPlayers).forEach { black ->
            if (black !in remainingPlayers) return@forEach

            val availablePlayers = remainingPlayers
                .sortedBy { it.rating.toRank().rankToString(false).toRankInt() }
                .toMutableList()
            val blackIndex = availablePlayers.indexOf(black)
            remainingPlayers.remove(black)
            availablePlayers.remove(black)
            val startIndex = cap(blackIndex - playerWindow, availablePlayers)
            val endIndex = cap(startIndex + playerWindow, availablePlayers)
            val opponents = availablePlayers.subList(startIndex, endIndex)

            // Remove last week opponent if any
            if (opponents.size > 1) previousPairings.firstNotNullOfOrNull {
                if (it.firstPlayerId == black.discordId) it.secondPlayerId
                else if (it.secondPlayerId == black.discordId) it.firstPlayerId
                else null
            }?.let { lastWeekId -> opponents.removeIf { it.discordId == lastWeekId } }

            val white = opponents.random()
            remainingPlayers.remove(white)

            val blackRank = black.rating.toRank().rankToString(false)
            val whiteRank = white.rating.toRank().rankToString(false)
            log(INFO, "Adding pairing ${black.discordId} [$blackRank] VS [$whiteRank] ${white.discordId}")
            pairings.add(Pair(LeaguePairing.pair(black, white, rush, date), black.rating.toRank().roundToInt()))
        }

        var message = ""
        pairings
            .sortedBy { it.second }
            .map { it.first }
            .forEachIndexed { index, pairing ->
                // Save pairing in DB
                DatabaseAccessor.savePairing(pairing)

                val firstPlayerLink = pairing.firstPlayerId.let { id ->
                    val user = DatabaseAccessor.ensureUser(id)
                    val player = DatabaseAccessor.ladderPlayer(user)
                    val rank = player?.rating?.toRank()?.rankToString(false) ?: "?"
                    val link = "${Config.Ladder.WEBSITE_URL}/players/$id"
                    "**[${user.name} ($rank)]($link)**"
                }

                val secondPlayerLink = pairing.secondPlayerId?.let { id ->
                    val user = DatabaseAccessor.ensureUser(id)
                    val player = DatabaseAccessor.ladderPlayer(user)
                    val rank = player?.rating?.toRank()?.rankToString(false) ?: "?"
                    val link = "${Config.Ladder.WEBSITE_URL}/players/$id"
                    "**[${user.name} ($rank)]($link)**"
                } ?: ""

                if (message.isNotBlank()) message += "\n"
                message += if (pairing.exempt) "$firstPlayerLink est bye."
                else "$firstPlayerLink :crossed_swords: $secondPlayerLink"

                if (index % 5 == 4) {
                    jda.publicMessage(
                        Config.League.CHANNEL_ID,
                        message,
                        "Oteai Gold - Appariements du Rush $rush"
                    )
                    message = ""
                }
            }

        if (message.isNotBlank()) {
            jda.publicMessage(
                Config.League.CHANNEL_ID,
                message,
                "Oteai Gold - Appariements du Rush $rush"
            )
            message = ""
        }

        jda.publicMessage(
            Config.League.CHANNEL_ID,
            "*Vous avez jusqu'à la fin du rush (dimanche prochain à 20h) pour jouer.*\n<:hisoGG:992415296826638406> **Bonnes parties ! <@&${Config.League.ROLE}>**",
        )
    }

    private fun sortByExtremes(list: List<LadderPlayer>): List<LadderPlayer> {
        val sorted = list.sortedBy { it.rating.toRank().rankToString(false).toRankInt() }.toMutableList()
        val extremesFirst = mutableListOf<LadderPlayer>()
        while (sorted.size > 0) {
            val player = if (extremesFirst.size % 2 == 0) sorted.first() else sorted.last()
            sorted.remove(player)
            extremesFirst.add(player)
        }
        return extremesFirst
    }

    private fun cap(index: Int, list: List<*>): Int = max(0, min(list.size, index))
}

