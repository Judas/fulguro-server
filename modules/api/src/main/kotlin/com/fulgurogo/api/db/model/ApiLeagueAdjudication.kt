package com.fulgurogo.api.db.model

import com.fulgurogo.common.utilities.DATE_ZONE
import com.fulgurogo.league.db.model.LeagueMatch
import java.time.format.DateTimeFormatter

/**
 * An administrator's ruling on a match: one award per side (`FORFEIT`, `EXEMPT`, `PARTICIPANT` or `WINNER`), and when it
 * was made. Null on a match nobody ruled on.
 *
 * Who made it is not served. It is in the database and in the log line, which is where a dispute gets settled, and the
 * site has no use for an administrator's Discord id.
 */
data class ApiLeagueAdjudication(
    val black: String,
    val white: String,
    val date: String?
) {
    companion object {
        /** Same ISO shape as [ApiLeagueSession], for the same reason: read by code, not printed as is. */
        private val ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")

        fun from(match: LeagueMatch): ApiLeagueAdjudication? {
            if (!match.isAdjudicated()) return null
            return ApiLeagueAdjudication(
                black = match.blackAward!!,
                white = match.whiteAward!!,
                date = match.adjudicated?.let { ISO.format(it.toInstant().atZone(DATE_ZONE)) }
            )
        }
    }
}
