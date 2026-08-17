package com.fulgurogo.house

/**
 * What a member asked for next season, recorded during the summer and applied when the season opens.
 *
 * The names are the values stored in `house_members.pending_action`, so this enum is the whole of the column's
 * vocabulary: the API parses a request body through [from] rather than trusting a string, and the season transition
 * reads it back through the same names.
 */
enum class HouseAction {
    /** Nothing happens. Same as no intention at all, and only ever recorded because a player said so out loud. */
    STAY,

    /**
     * Moved into the house the player named when the season opens. The target is recorded alongside, in
     * `house_members.pending_house_id`, so this action alone does not say where the player is going — see
     * [com.fulgurogo.house.db.model.HouseMember.pendingHouseId].
     */
    CHANGE,

    /** The membership is deleted when the season opens. Points already earned stay with the house. */
    LEAVE;

    companion object {
        /** [value] as an action, or null when it is not one — an unknown string is a bad request, not a default. */
        fun from(value: String?): HouseAction? = entries.firstOrNull { it.name.equals(value?.trim(), true) }
    }
}
