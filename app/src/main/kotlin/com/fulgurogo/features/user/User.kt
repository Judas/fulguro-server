package com.fulgurogo.features.user

import com.fulgurogo.features.user.egf.EgfClient
import com.fulgurogo.features.user.ffg.FfgClient
import com.fulgurogo.features.user.fox.FoxClient
import com.fulgurogo.features.user.igs.IgsClient
import com.fulgurogo.features.user.kgs.KgsClient
import com.fulgurogo.features.user.ogs.OgsClient
import com.fulgurogo.utilities.userName
import net.dv8tion.jda.api.JDA
import java.util.*

data class User(
    val discordId: String,
    val name: String? = null,
    val avatar: String? = null,
    val titles: String? = null,
    val lastGameScan: Date? = null,
    val kgsId: String? = null,
    val kgsPseudo: String? = null,
    val kgsRank: String? = null,
    val ogsId: String? = null,
    val ogsPseudo: String? = null,
    val ogsRank: String? = null,
    val foxId: String? = null,
    val foxPseudo: String? = null,
    val foxRank: String? = null,
    val igsId: String? = null,
    val igsPseudo: String? = null,
    val igsRank: String? = null,
    val ffgId: String? = null,
    val ffgPseudo: String? = null,
    val ffgRank: String? = null,
    val egfId: String? = null,
    val egfPseudo: String? = null,
    val egfRank: String? = null
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
        val kgs = fetchUser(UserAccount.KGS)
        val ogs = fetchUser(UserAccount.OGS)
        val fox = fetchUser(UserAccount.FOX)
        val igs = fetchUser(UserAccount.IGS)
        val ffg = fetchUser(UserAccount.FFG)
        val egf = fetchUser(UserAccount.EGF)

        return User(
            discordId = discordId,
            name = jda.userName(discordId),
            avatar = jda.getUserById(discordId)?.effectiveAvatarUrl,
            titles = titles,
            lastGameScan = lastGameScan,
            kgsId = kgsId,
            kgsPseudo = kgs?.pseudo(),
            kgsRank = kgs?.rank(),
            ogsId = ogsId,
            ogsPseudo = ogs?.pseudo(),
            ogsRank = ogs?.rank(),
            foxId = fox?.id(),
            foxPseudo = foxPseudo,
            foxRank = fox?.rank(),
            igsId = igsId,
            igsPseudo = igs?.pseudo(),
            igsRank = igs?.rank(),
            ffgId = ffgId,
            ffgPseudo = ffg?.pseudo(),
            ffgRank = ffg?.rank(),
            egfId = egfId,
            egfPseudo = egf?.pseudo(),
            egfRank = egf?.rank()
        )
    }

    private fun fetchUser(account: UserAccount): ServerUser? = when (account) {
        UserAccount.KGS -> (UserAccount.KGS.client as KgsClient).user(kgsId)
        UserAccount.OGS -> (UserAccount.OGS.client as OgsClient).user(ogsId)
        UserAccount.FOX -> (UserAccount.FOX.client as FoxClient).user(foxPseudo)
        UserAccount.IGS -> (UserAccount.IGS.client as IgsClient).user(igsId)
        UserAccount.FFG -> (UserAccount.FFG.client as FfgClient).user(ffgId)
        UserAccount.EGF -> (UserAccount.EGF.client as EgfClient).user(egfId)
        else -> null
    }
}
