package com.fulgurogo.features.api

import com.fulgurogo.features.database.DatabaseAccessor
import com.fulgurogo.features.user.UserAccount
import com.fulgurogo.utilities.GenerateNoArgConstructor

@GenerateNoArgConstructor
data class ApiPlayer(
    val discordId: String,
    val name: String? = null,
    val avatar: String? = null,

    val rating: Double? = null,
    val tierRank: Int? = null,
    val tierName: String? = null,
    val stable: Boolean = false,

    var fgcValidation: ApiFgcValidation? = null,
    var games: MutableList<ApiGame>? = null,
    var accounts: MutableList<ApiAccount>? = null,
    var exam: ApiExamPlayer? = null
) {
    companion object {
        fun default(discordId: String): ApiPlayer? = DatabaseAccessor.user(UserAccount.DISCORD, discordId)?.let {
            ApiPlayer(
                discordId,
                it.name,
                it.avatar
            )
        }
    }
}
