package com.fulgurogo.house

import com.fulgurogo.house.db.HouseDatabaseAccessor
import com.fulgurogo.house.db.model.House

/**
 * The balanced draw: a player never picks their house, they are drawn into one of the emptiest.
 *
 * It lives in the house module rather than in the API handler because there are two callers, and they have to draw the
 * same way: joining during the season (step 7) and applying a `CHANGE` intention when a season opens (step 9).
 */
object HouseAssignment {
    /**
     * A house drawn among those with the fewest members, or null when there is nothing to draw from — which only
     * happens if the `houses` table was never seeded.
     *
     * [excluding] leaves one house out, which is what a change needs: being redrawn into the house you asked to leave
     * is not a change. Restricting to the minimum *after* the exclusion, not before, is what keeps the draw balanced
     * over the three houses that remain.
     *
     * The count of a house missing from [HouseDatabaseAccessor.memberCounts] reads as 0. That map is driven from
     * `houses` by a LEFT JOIN and always holds all four, so this is a fallback that should not fire; it errs towards
     * drawing that house, which is the direction the draw exists to favour.
     */
    fun draw(excluding: Int? = null): House? {
        val candidates = HouseDatabaseAccessor.houses().filter { it.id != excluding }
        if (candidates.isEmpty()) return null

        val counts = HouseDatabaseAccessor.memberCounts()
        val smallest = candidates.minOf { counts[it.id] ?: 0 }
        return candidates.filter { (counts[it.id] ?: 0) == smallest }.random()
    }
}
