package com.fulgurogo.features.user

import com.fulgurogo.features.user.egf.EgfClient
import com.fulgurogo.features.user.ffg.FfgClient
import com.fulgurogo.features.user.fox.FoxClient
import com.fulgurogo.features.user.igs.IgsClient
import com.fulgurogo.features.user.kgs.KgsClient
import com.fulgurogo.features.user.ogs.OgsClient

enum class UserAccount(
    val fullName: String,
    val databaseId: String,
    val client: UserAccountClient
) {
    DISCORD(
        "Discord",
        "discord_id",
        NoOpUserAccountClient()
    ),
    KGS(
        "KGS",
        "kgs_id",
        KgsClient()
    ),
    OGS(
        "OGS",
        "ogs_id",
        OgsClient()
    ),
    FOX(
        "FOX",
        "fox_pseudo",
        FoxClient()
    ),
    IGS(
        "IGS",
        "igs_id",
        IgsClient()
    ),
    FFG(
        "FFG",
        "ffg_id",
        FfgClient()
    ),
    EGF(
        "EGF",
        "egf_id",
        EgfClient()
    );

    companion object {
        private val ACCOUNTS: List<UserAccount> = listOf(DISCORD, KGS, OGS, FOX, IGS, FFG, EGF)
        val SUPPORTED_PLAYABLE_ACCOUNTS = listOf(KGS, OGS, FOX)

        fun find(serverName: String): UserAccount? = ACCOUNTS.find { it.name == serverName }
    }
}
