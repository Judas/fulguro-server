package com.fulgurogo.features.api

import com.fulgurogo.Config.Ladder.INITIAL_DEVIATION
import com.fulgurogo.Config.Ladder.INITIAL_RATING
import com.fulgurogo.features.database.DatabaseAccessor
import com.fulgurogo.features.user.UserAccount

data class ApiPlayer(
    val discordId: String,
    val name: String? = null,
    val avatar: String? = null,

    val rating: Double,
    val deviation: Double,
    val ranked: Boolean,
    val tierRank: Int,
    val tierName: String,
    val stable: Boolean,

    var stability: ApiStability? = null,
    var games: MutableList<ApiGame>? = null,
    var accounts: MutableList<ApiAccount>? = null,
    var exam: ApiExamPlayer? = null
) {
    companion object {
        fun default(discordId: String): ApiPlayer? = DatabaseAccessor.user(UserAccount.DISCORD, discordId)?.let {
            ApiPlayer(
                discordId,
                it.name,
                it.avatar,
                INITIAL_RATING,
                INITIAL_DEVIATION,
                false,
                2,
                "Initié",
                false
            )
        }
    }
}
