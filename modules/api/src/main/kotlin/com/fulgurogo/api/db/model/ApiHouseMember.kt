package com.fulgurogo.api.db.model

import com.fulgurogo.house.db.model.HouseRankedMember

/**
 * One line of a house ranking: who, where they stand, and what they have scored for that house this season.
 *
 * [rank] is a competition rank — equal totals share it and the next one skips (1, 2, 2, 4) — so it is not the position
 * in the list and the site must print it rather than count rows.
 */
data class ApiHouseMember(
    val discordId: String,
    val discordName: String? = null,
    val discordAvatar: String? = null,
    val rank: Int,
    val points: ApiHousePoints
) {
    companion object {
        fun from(member: HouseRankedMember): ApiHouseMember = ApiHouseMember(
            discordId = member.discordId,
            discordName = member.discordName,
            discordAvatar = member.discordAvatar,
            rank = member.rank,
            points = ApiHousePoints.from(member)
        )
    }
}
