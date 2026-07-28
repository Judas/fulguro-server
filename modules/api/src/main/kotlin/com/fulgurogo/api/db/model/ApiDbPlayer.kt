package com.fulgurogo.api.db.model

import com.fulgurogo.common.utilities.GenerateNoArgConstructor

@GenerateNoArgConstructor
data class ApiDbPlayer(
    val discordId: String,
    val discordName: String? = null,
    val discordAvatar: String? = null,
    val kgsId: String? = null,
    val kgsRank: String? = null,
    val ogsId: Int? = null,
    val ogsName: String? = null,
    val ogsRank: String? = null,
    val rating: Double = 0.0,
    val tierRank: Int = 0,
    val tierName: String? = null,
    val totalRankedGames: Int = 0,
    val goldRankedGames: Int = 0,
) {
    fun toApiPlayer() = ApiPlayer(
        discordId = discordId,
        discordName = discordName,
        discordAvatar = discordAvatar,
        accounts = toApiPlayerAccounts(),
        rating = rating,
        tierRank = tierRank,
        tierName = tierName,
        totalRankedGames = totalRankedGames,
        goldRankedGames = goldRankedGames
    )

    /**
     * `api_players` LEFT JOINs each platform table, so all of a platform's columns are null exactly when the player has
     * no account there — any one of them can answer "is this linked?".
     *
     * Each platform is keyed on the value the **user supplied** when linking, which is its id.
     */
    fun toApiPlayerAccounts(): List<ApiPlayerAccount> =
        listOf(
            "KGS" to kgsId,
            "OGS" to ogsId
        )
            .filter { it.second != null }
            .mapNotNull {
                when (it.first) {
                    "KGS" -> ApiPlayerAccount(
                        server = "KGS",
                        id = kgsId,
                        name = kgsId,
                        rank = kgsRank.orUnknown(),
                        link = "https://www.gokgs.com/graphPage.jsp?user=$kgsId"
                    )

                    "OGS" -> ApiPlayerAccount(
                        server = "OGS",
                        id = ogsId.toString(),
                        name = ogsName,
                        rank = ogsRank.orUnknown(),
                        link = "https://online-go.com/player/$ogsId"
                    )

                    else -> null
                }
            }
}

/**
 * Ranks are stored as "?" when unknown, so this is belt and braces -- but it is declared on String? because the
 * previous form was an extension on non-null String that called isNullOrBlank(), so a null rank stayed null instead
 * of becoming "?".
 */
private fun String?.orUnknown(): String = if (isNullOrBlank()) "?" else this