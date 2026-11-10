package com.fulgurogo.ogs

import com.fulgurogo.ogs.websocket.model.OgsWsGameLight
import java.time.Instant

/** The live games of a `gamelist/query` answer, and when that answer arrived. */
data class OgsLiveSnapshot(
    val games: List<OgsWsGameLight>,
    val receivedAt: Instant,
)

/**
 * The last live game list OGS sent [OgsRealTimeService], before it narrows it down to games between two members.
 *
 * In memory and not in `ogs_games`, because that table lags behind what is being played. The WebSocket only writes games
 * where *both* players are members; a game against an outsider only gets there through the REST poll, a stalest-first
 * walk that reaches a given player once every (members × 15s). The same delay applies the other way: a game whose end
 * the WebSocket missed stays `unfinished` until the poll comes back to one of its players. Both settle eventually, but
 * a live list that is minutes behind is not a live list.
 *
 * The list is the whole answer, so it is replaced, never merged: a game that has ended is simply absent from the next
 * one. Null until the first answer after a start. The reader decides how old is too old, from [OgsLiveSnapshot.receivedAt].
 */
object OgsLiveGames {
    @Volatile
    var snapshot: OgsLiveSnapshot? = null
        private set

    internal fun publish(games: List<OgsWsGameLight>) {
        snapshot = OgsLiveSnapshot(games, Instant.now())
    }
}
