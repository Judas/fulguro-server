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
import java.util.*
import kotlin.math.max
import kotlin.math.min

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
        DatabaseAccessor.leaguePairings(rush).filterNot { it.exempt }.filter { it.winnerId == null }
            .forEach { pairing ->
                // Search for first ladder game between given players
                ladderGames.filter { it.date.after(pairing.date) }
                    .firstOrNull { game -> pairing.containsPlayers(game.mainPlayerId, game.opponentId) }?.let { game ->
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

        // Get all players with league role
        val rushPlayers = jda.getGuildById(Config.GUILD_ID)?.findMembers { member ->
            member.roles.any { it.id == Config.League.ROLE }
        }?.get()?.mapNotNull { DatabaseAccessor.ladderPlayer(DatabaseAccessor.ensureUser(it.user)) }?.toMutableList()
            ?: mutableListOf()

        log(INFO, "Found ${rushPlayers.size} players with the league role")

        val pairings = mutableListOf<LeaguePairing>()

        // If odd number of players, remove one randomly
        if (rushPlayers.size % 2 == 1) {
            val exempted = rushPlayers.random()
            rushPlayers.remove(exempted)
            pairings.exemptPlayer(exempted, rush, date)
        }

        // Split players by league group
        val playerMap = splitByGroup(rushPlayers)
        var uprankedPlayer: LadderPlayer? = null

        LeagueGroup.values().forEach { group ->
            // Sort by level
            val remainingPlayers =
                playerMap[group]?.sortedBy { it.rating.toRank().rankToString(false).toRankInt() }?.toMutableList()
                    ?: mutableListOf()

            log(INFO, "Group ${group.name} : ${remainingPlayers.size} players")

            if (remainingPlayers.isNotEmpty()) {
                // Match upranked player with weakest, if any
                if (uprankedPlayer != null) {
                    log(INFO, "Pairing upranked player ${uprankedPlayer!!.discordId}")
                    pairings.addMatch(uprankedPlayer!!, remainingPlayers.last(), rush, date)
                    remainingPlayers.removeLast()
                    uprankedPlayer = null
                }

                // If odd number, uprank first (strongest player of group)
                if (remainingPlayers.size % 2 == 1) {
                    uprankedPlayer = remainingPlayers.first()
                    log(INFO, "Upranking player ${uprankedPlayer!!.discordId}")
                    remainingPlayers.removeFirst()
                }

                remainingPlayers.toMutableList().forEach remainingLoop@{ mainPlayer ->
                    if (mainPlayer !in remainingPlayers) return@remainingLoop
                    remainingPlayers.remove(mainPlayer)

                    val opponent = remainingPlayers.random()
                    remainingPlayers.remove(opponent)

                    pairings.addMatch(mainPlayer, opponent, rush, date)
                }
            }
        }

        var message = ""
        pairings.forEachIndexed { index, pairing ->
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
                    Config.League.CHANNEL_ID, message, "Oteai Gold - Appariements du Rush $rush"
                )
                message = ""
            }
        }

        if (message.isNotBlank()) {
            jda.publicMessage(
                Config.League.CHANNEL_ID, message, "Oteai Gold - Appariements du Rush $rush"
            )
            message = ""
        }

        jda.publicMessage(
            Config.League.CHANNEL_ID,
            "*Vous avez jusqu'à la fin du rush (dimanche prochain à 20h) pour jouer.*\n<:hisoGG:992415296826638406> **Bonnes parties ! <@&${Config.League.ROLE}>**",
        )
    }

    private fun MutableList<LeaguePairing>.addMatch(black: LadderPlayer, white: LadderPlayer, rush: Int, date: Date) {
        val blackRank = black.rating.toRank().rankToString(false)
        val whiteRank = white.rating.toRank().rankToString(false)
        log(INFO, "Adding pairing ${black.discordId} [$blackRank] VS [$whiteRank] ${white.discordId}")
        this.add(LeaguePairing.pair(black, white, rush, date))
    }

    private fun MutableList<LeaguePairing>.exemptPlayer(exempted: LadderPlayer, rush: Int, date: Date) {
        log(INFO, "Exempting player ${exempted.discordId}")
        this.add(LeaguePairing.exempted(exempted, rush, date))
    }

    private fun splitByGroup(list: List<LadderPlayer>): Map<LeagueGroup, MutableList<LadderPlayer>> {
        val map = mutableMapOf<LeagueGroup, MutableList<LadderPlayer>>()
        list.forEach {
            val rankInt = it.rating.toRank().rankToString(false).toRankInt()
            val group = when {
                rankInt >= 15 -> LeagueGroup.TIN
                rankInt >= 10 -> LeagueGroup.COPPER
                rankInt >= 5 -> LeagueGroup.BRONZE
                rankInt >= 1 -> LeagueGroup.SILVER
                else -> LeagueGroup.GOLD
            }

            if (!map.containsKey(group) || map[group] == null) map[group] = mutableListOf(it)
            else map[group]!!.add(it)
        }
        return map
    }

    private fun cap(index: Int, list: List<*>): Int = max(0, min(list.size, index))
}

