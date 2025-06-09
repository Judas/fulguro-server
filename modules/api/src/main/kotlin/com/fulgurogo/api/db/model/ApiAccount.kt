package com.fulgurogo.api.db.model

import com.fulgurogo.common.utilities.GenerateNoArgConstructor

@GenerateNoArgConstructor
data class ApiAccount(
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
    val egfRank: String? = null
) {
    fun toApiPlayerAccounts(): List<ApiPlayerAccount> = listOfNotNull(kgsId, ogsId, foxName, igsId, ffgId, egfId)
        .mapNotNull {
            when (it) {
                kgsId -> ApiPlayerAccount(
                    server = "KGS",
                    id = kgsId,
                    name = kgsId,
                    rank = kgsRank?.fallbackTo("?"),
                    link = "https://www.gokgs.com/graphPage.jsp?user=$kgsId"
                )

                ogsId -> ApiPlayerAccount(
                    server = "OGS",
                    id = ogsId.toString(),
                    name = ogsName,
                    rank = ogsRank?.fallbackTo("?"),
                    link = "https://online-go.com/player/$ogsId"
                )

                foxName -> ApiPlayerAccount(
                    server = "FOX",
                    id = foxId.toString(),
                    name = foxName,
                    rank = foxRank?.fallbackTo("?")
                )

                igsId -> ApiPlayerAccount(
                    server = "IGS",
                    id = igsId,
                    name = igsId,
                    rank = igsRank?.fallbackTo("?")
                )

                ffgId -> ApiPlayerAccount(
                    server = "FFG",
                    id = ffgId,
                    name = ffgName,
                    rank = ffgRank?.fallbackTo("?"),
                    link = "https://ffg.jeudego.org/php/affichePersonne.php?id=$ffgId"
                )

                egfId -> ApiPlayerAccount(
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