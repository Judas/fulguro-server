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
    val foxId: Int? = null,
    val foxName: String? = null,
    val foxRank: String? = null,
    val igsId: String? = null,
    val igsRank: String? = null,
    val ffgId: String? = null,
    val ffgName: String? = null,
    val ffgRank: String? = null,
    val egfId: String? = null,
    val egfName: String? = null,
    val egfRank: String? = null,
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

    fun toApiPlayerAccounts(): List<ApiPlayerAccount> =
        listOf(
            "KGS" to kgsId,
            "OGS" to ogsId,
            "FOX" to foxName,
            "IGS" to igsId,
            "FFG" to ffgId,
            "EGF" to egfId
        )
            .filter { it.second != null }
            .mapNotNull {
                when (it.first) {
                    "KGS" -> ApiPlayerAccount(
                        server = "KGS",
                        id = kgsId,
                        name = kgsId,
                        rank = kgsRank?.fallbackTo("?"),
                        link = "https://www.gokgs.com/graphPage.jsp?user=$kgsId"
                    )

                    "OGS" -> ApiPlayerAccount(
                        server = "OGS",
                        id = ogsId.toString(),
                        name = ogsName,
                        rank = ogsRank?.fallbackTo("?"),
                        link = "https://online-go.com/player/$ogsId"
                    )

                    "FOX" -> ApiPlayerAccount(
                        server = "FOX",
                        id = foxId.toString(),
                        name = foxName,
                        rank = foxRank?.fallbackTo("?")
                    )

                    "IGS" -> ApiPlayerAccount(
                        server = "IGS",
                        id = igsId,
                        name = igsId,
                        rank = igsRank?.fallbackTo("?")
                    )

                    "FFG" -> ApiPlayerAccount(
                        server = "FFG",
                        id = ffgId,
                        name = ffgName,
                        rank = ffgRank?.fallbackTo("?"),
                        link = "https://ffg.jeudego.org/php/affichePersonne.php?id=$ffgId"
                    )

                    "EGF" -> ApiPlayerAccount(
                        server = "EGF",
                        id = egfId,
                        name = egfName,
                        rank = egfRank?.fallbackTo("?"),
                        link = "https://www.europeangodatabase.eu/EGD/Player_Card.php?key=$egfId"
                    )

                    else -> null
                }
            }
}

private fun String.fallbackTo(fallback: String) = if (isNullOrBlank()) fallback else this