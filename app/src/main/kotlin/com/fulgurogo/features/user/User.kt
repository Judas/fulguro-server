package com.fulgurogo.features.user

import com.fulgurogo.common.config.Config
import com.fulgurogo.features.api.ApiAccount
import com.fulgurogo.features.ladder.RatingCalculator
import com.fulgurogo.features.user.egf.EgfClient
import com.fulgurogo.features.user.ffg.FfgClient
import com.fulgurogo.features.user.fox.FoxClient
import com.fulgurogo.features.user.igs.IgsClient
import com.fulgurogo.features.user.kgs.KgsClient
import com.fulgurogo.features.user.ogs.OgsClient
import com.fulgurogo.common.utilities.GenerateNoArgConstructor
import com.fulgurogo.utilities.userName
import net.dv8tion.jda.api.JDA
import java.util.*

@GenerateNoArgConstructor
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
    val egfRank: String? = null,
    val rating: Double? = null,
    val tierRank: Int? = null,
    val tierName: String? = null
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

    fun cloneUserWithUpdatedProfile(jda: JDA, full: Boolean): User =
        if (full) {
            val accountMap: MutableMap<UserAccount, ServerUser?> = mutableMapOf()
            accountMap[UserAccount.KGS] = (UserAccount.KGS.client as KgsClient).user(kgsId)
            accountMap[UserAccount.OGS] = (UserAccount.OGS.client as OgsClient).user(ogsId)
            accountMap[UserAccount.FOX] = (UserAccount.FOX.client as FoxClient).user(foxPseudo)
            accountMap[UserAccount.IGS] = (UserAccount.IGS.client as IgsClient).user(igsId)
            accountMap[UserAccount.FFG] = (UserAccount.FFG.client as FfgClient).user(ffgId)
            accountMap[UserAccount.EGF] = (UserAccount.EGF.client as EgfClient).user(egfId)

            val rating = RatingCalculator.rate(accountMap)

            val user = User(
                discordId = discordId,
                name = if (Config.get("debug").toBoolean()) name else jda.userName(discordId),
                avatar = jda.getUserById(discordId)?.effectiveAvatarUrl ?: Config.get("ladder.default.avatar"),
                titles = titles,
                lastGameScan = lastGameScan,
                kgsId = kgsId,
                kgsPseudo = accountMap[UserAccount.KGS]?.pseudo(),
                kgsRank = accountMap[UserAccount.KGS]?.rank(),
                ogsId = ogsId,
                ogsPseudo = accountMap[UserAccount.OGS]?.pseudo(),
                ogsRank = accountMap[UserAccount.OGS]?.rank(),
                foxId = accountMap[UserAccount.FOX]?.id(),
                foxPseudo = foxPseudo,
                foxRank = accountMap[UserAccount.FOX]?.rank(),
                igsId = igsId,
                igsPseudo = accountMap[UserAccount.IGS]?.pseudo(),
                igsRank = accountMap[UserAccount.IGS]?.rank(),
                ffgId = ffgId,
                ffgPseudo = accountMap[UserAccount.FFG]?.pseudo(),
                ffgRank = accountMap[UserAccount.FFG]?.rank(),
                egfId = egfId,
                egfPseudo = accountMap[UserAccount.EGF]?.pseudo(),
                egfRank = accountMap[UserAccount.EGF]?.rank(),
                rating = rating?.rating,
                tierRank = rating?.tier?.rank,
                tierName = rating?.tier?.name
            )

            user
        } else {
            User(
                discordId = discordId,
                name = jda.userName(discordId),
                avatar = jda.getUserById(discordId)?.effectiveAvatarUrl ?: Config.get("ladder.default.avatar")
            )
        }

    fun toApiAccounts(): MutableList<ApiAccount> {
        val accounts = mutableListOf<ApiAccount>()
        kgsId?.let {
            accounts.add(
                ApiAccount(
                    name = UserAccount.KGS.fullName,
                    id = kgsId,
                    pseudo = kgsPseudo,
                    rank = kgsRank,
                    link = "https://www.gokgs.com/graphPage.jsp?user=$kgsId"
                )
            )
        }
        ogsId?.let {
            accounts.add(
                ApiAccount(
                    name = UserAccount.OGS.fullName,
                    id = ogsId,
                    pseudo = ogsPseudo,
                    rank = ogsRank,
                    link = "https://online-go.com/player/$ogsId"
                )
            )
        }
        foxPseudo?.let {
            accounts.add(
                ApiAccount(
                    name = UserAccount.FOX.fullName,
                    id = foxId,
                    pseudo = foxPseudo,
                    rank = foxRank
                )
            )
        }
        igsId?.let {
            accounts.add(
                ApiAccount(
                    name = UserAccount.IGS.fullName,
                    id = igsId,
                    pseudo = igsPseudo,
                    rank = igsRank
                )
            )
        }
        ffgId?.let {
            accounts.add(
                ApiAccount(
                    name = UserAccount.FFG.fullName,
                    id = ffgId,
                    pseudo = ffgPseudo,
                    rank = ffgRank,
                    link = "https://ffg.jeudego.org/php/affichePersonne.php?id=$ffgId"
                )
            )
        }
        egfId?.let {
            accounts.add(
                ApiAccount(
                    name = UserAccount.EGF.fullName,
                    id = egfId,
                    pseudo = egfPseudo,
                    rank = egfRank,
                    link = "https://www.europeangodatabase.eu/EGD/Player_Card.php?key=$egfId"
                )
            )
        }
        return accounts
    }
}
