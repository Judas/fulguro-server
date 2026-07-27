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

    /**
     * `api_players` LEFT JOINs each platform table, so all of a platform's columns are null exactly when the player has
     * no account there — any one of them can answer "is this linked?".
     *
     * Each platform is keyed on the value the **user supplied** when linking, which is the id everywhere except FOX.
     * On FOX the user gives a nickname and `fox_id` is derived afterwards: `addUser` writes the placeholder `'?'`
     * (which the INT column stores as 0) and only the first successful refresh replaces it. So `fox_name` is the
     * authoritative column there — do not "align" it with the others. It is also the safer choice, since a VARCHAR
     * always maps to `String?`, whereas `fox_id` would silently map to null if that column's type ever changed to hold
     * the placeholder honestly (reads set `throwOnMappingFailure(false)`).
     */
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

                    "FOX" -> ApiPlayerAccount(
                        server = "FOX",
                        id = foxId.toString(),
                        name = foxName,
                        rank = foxRank.orUnknown()
                    )

                    "IGS" -> ApiPlayerAccount(
                        server = "IGS",
                        id = igsId,
                        name = igsId,
                        rank = igsRank.orUnknown()
                    )

                    "FFG" -> ApiPlayerAccount(
                        server = "FFG",
                        id = ffgId,
                        name = ffgName,
                        rank = ffgRank.orUnknown(),
                        link = "https://ffg.jeudego.org/php/affichePersonne.php?id=$ffgId"
                    )

                    "EGF" -> ApiPlayerAccount(
                        server = "EGF",
                        id = egfId,
                        name = egfName,
                        rank = egfRank.orUnknown(),
                        link = "https://www.europeangodatabase.eu/EGD/Player_Card.php?key=$egfId"
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