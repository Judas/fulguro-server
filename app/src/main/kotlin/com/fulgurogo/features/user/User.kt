package com.fulgurogo.features.user

import com.fulgurogo.features.user.fox.FoxClient
import com.fulgurogo.features.user.ogs.OgsClient
import com.fulgurogo.utilities.userName
import net.dv8tion.jda.api.JDA
import java.util.*

data class User(
    val discordId: String,
    val name: String? = null,
    val avatar: String? = null,
    val kgsId: String? = null,
    val kgsPseudo: String? = null,
    val ogsId: String? = null,
    val ogsPseudo: String? = null,
    val foxId: String? = null,
    val foxPseudo: String? = null,
    val igsId: String? = null,
    val ffgId: String? = null,
    val egfId: String? = null,
    val titles: String? = null,
    val lastGameScan: Date? = null
) {
    companion object {
        fun dummyFrom(discordId: String, account: UserAccount, accountId: String): User = when (account) {
            UserAccount.KGS -> User(discordId, kgsId = accountId)
            UserAccount.OGS -> User(discordId, ogsId = accountId)
            UserAccount.FOX -> User(discordId, foxPseudo = accountId)
            UserAccount.IGS -> User(discordId, igsId = accountId)
            UserAccount.FFG -> User(discordId, ffgId = accountId)
            UserAccount.EGF -> User(discordId, egfId = accountId)
            else -> User(discordId)
        }
    }

    fun cloneUserWithUpdatedProfile(jda: JDA): User {
        return User(
            discordId = discordId,
            name = jda.userName(discordId),
            avatar = jda.getUserById(discordId)?.effectiveAvatarUrl,
            kgsId = kgsId,
            kgsPseudo = kgsId,
            ogsId = ogsId,
            ogsPseudo = fetchOgsPseudo(),
            foxId = fetchFoxId(),
            foxPseudo = foxPseudo,
            igsId = igsId,
            ffgId = ffgId,
            egfId = egfId,
            titles = titles,
            lastGameScan = lastGameScan
        )
    }

    private fun fetchOgsPseudo(): String? = ogsId?.let {
        ogsPseudo ?: (UserAccount.OGS.client as OgsClient).user(ogsId)?.username
    }

    private fun fetchFoxId(): String? = foxPseudo?.let {
        foxId ?: (UserAccount.FOX.client as FoxClient).user(foxPseudo)?.id()
    }
}
