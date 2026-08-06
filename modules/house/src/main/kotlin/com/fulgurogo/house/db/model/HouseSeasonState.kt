package com.fulgurogo.house.db.model

import com.fulgurogo.common.utilities.GenerateNoArgConstructor
import java.util.*

/**
 * Where one season stands in its lifecycle: the row of `house_seasons` that makes the once-a-year events happen once.
 *
 * Nothing here is derived. A season's dates are known from its name — [com.fulgurogo.house.HouseSeason] computes them —
 * so these three columns exist only to record what has already been *done*, which no calendar can answer. Same role as
 * the Exam Hunter's `hasPromotionScore`, in a table rather than deduced.
 *
 * A missing row means a season nothing has happened to yet, which is why [opened] being null and the row being absent
 * have to read the same to every caller.
 */
@GenerateNoArgConstructor
data class HouseSeasonState(
    val season: String,
    /**
     * When the holiday intentions were applied and the season started counting. Null means they have not been, and it is
     * also what tells a season that really ran from one that only ever existed as a name — the guard that stops the
     * first deployment from announcing the closure of a season with no members and no points.
     */
    val opened: Date? = null,
    /** When the end-of-season recap went out. Null means it has not. */
    val closed: Date? = null,
    /** When the daily ranking was last announced. Read by step 10, so that a 10-minute tick posts it once a morning. */
    val lastRanking: Date? = null
)
