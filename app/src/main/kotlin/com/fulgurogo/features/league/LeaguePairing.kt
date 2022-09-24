package com.fulgurogo.features.league

import com.fulgurogo.features.ladder.LadderPlayer
import java.util.*

data class LeaguePairing(
    val id: Int,
    val rush: Int,
    val date: Date,
    val firstPlayerId: String,
    val secondPlayerId: String?,
    val gameId: Int?,
    val winnerId: String?,
    val exempt: Boolean,
) {
    companion object {
        fun exempted(player: LadderPlayer, rush: Int, date: Date): LeaguePairing =
            LeaguePairing(-1, rush, date, player.discordId, null, null, null, true)

        fun pair(main: LadderPlayer, opponent: LadderPlayer, rush: Int, date: Date): LeaguePairing =
            LeaguePairing(-1, rush, date, main.discordId, opponent.discordId, null, null, false)
    }

    fun containsPlayers(mainPlayerId: String, opponentId: String?) =
        (firstPlayerId == mainPlayerId && secondPlayerId == opponentId)
                || (secondPlayerId == mainPlayerId && firstPlayerId == opponentId)
}
