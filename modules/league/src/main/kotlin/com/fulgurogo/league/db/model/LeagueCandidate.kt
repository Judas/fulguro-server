package com.fulgurogo.league.db.model

import com.fulgurogo.common.utilities.GenerateNoArgConstructor

/**
 * An active member the draw can actually pair: they are in an academy, in a house, they have an OGS account, they are
 * registered with the OGS league, and their gold rating is usable.
 *
 * Every one of those conditions is applied by the single join in `LeagueDatabaseAccessor.candidates`, so a candidate that
 * comes out of it needs no further checking. That is deliberate: spreading them between the query and the draw is how a
 * player ends up paired against somebody OGS has never heard of, and the match creation then fails for a reason nothing
 * in the draw explains.
 */
@GenerateNoArgConstructor
data class LeagueCandidate(
    val discordId: String,
    /** Frozen onto the match at draw time, which is why the draw carries it rather than looking it up again. */
    val houseId: Int,
    /**
     * The gold rating, which the draw minimises the difference of. Guaranteed usable by the query — strictly positive
     * and not in error — because a rating of 0 would drag whoever it belongs to to one end of the ladder and pair them
     * against the wrong player every session, silently.
     */
    val rating: Double
)
