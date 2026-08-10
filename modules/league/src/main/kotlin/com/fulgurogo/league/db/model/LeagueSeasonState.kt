package com.fulgurogo.league.db.model

import com.fulgurogo.common.utilities.GenerateNoArgConstructor
import java.util.*

/**
 * Where one league season stands in its lifecycle: the row of `league_seasons` that makes the once-a-year events happen
 * once.
 *
 * Same role, and the same reason for existing, as `HouseSeasonState`: a season's dates are known from its name, so
 * these two columns only record what has already been *done*, which no calendar can answer.
 *
 * A missing row means a season nothing has happened to yet, so [opened] being null and the row being absent have to
 * read the same to every caller.
 */
@GenerateNoArgConstructor
data class LeagueSeasonState(
    val season: String,
    /**
     * When the season started. Null means it has not, and that is also what tells a season that really ran from one
     * that only ever existed as a name — the guard that stops a first deployment from announcing the closing recap of a
     * league with no members and no matches.
     */
    val opened: Date? = null,
    /** When the closing recap went out. Null means it has not. */
    val closed: Date? = null
)
