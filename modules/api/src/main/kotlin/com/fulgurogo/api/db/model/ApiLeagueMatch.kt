package com.fulgurogo.api.db.model

import com.fulgurogo.league.db.model.LeagueMatch

/**
 * One pairing as the site shows it: the two players, the spectator link, and how it ended.
 *
 * ⚠ **[spectatorLink] is the only link that leaves this server.** `black_invite` and `white_invite` never do, on any
 * route, not even on the profile of the player they belong to. OGS's own code says the assumption is that only the right
 * user has been given the key, and no route here is authenticated — so publishing a player link would let anyone play
 * anyone's match. It is also why resending a lost link is a manual repair.
 *
 * [result] has **three** states and the site has to tell them apart. `null` means the session is running and the match
 * has not been played; `"unplayed"` means the session was settled without it being played, so it will never count; and
 * anything else is a real result. Showing the first two alike would make a forfeit look like a game still to play.
 *
 * [winnerDiscordId] is computed rather than left to be deduced. [result] carries `"black"` or `"white"`, and matching that
 * to a player means knowing which side they were on — the server knows, so it says. Null when nobody won, which covers an
 * annulled match, a drawn one and one not yet played.
 */
data class ApiLeagueMatch(
    val black: ApiLeagueMember,
    val white: ApiLeagueMember,
    val spectatorLink: String? = null,
    val result: String? = null,
    val winnerDiscordId: String? = null
) {
    companion object {
        fun from(match: LeagueMatch, black: ApiLeagueMember, white: ApiLeagueMember): ApiLeagueMatch = ApiLeagueMatch(
            black = black,
            white = white,
            spectatorLink = match.spectatorLink,
            result = match.result,
            winnerDiscordId = match.winner()
        )
    }
}
