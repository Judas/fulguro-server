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
    val client: UserAccountClient,
    val ratingWeight: Double
) {
    DISCORD(
        "Discord",
        "discord_id",
        NoOpUserAccountClient(),
        0.0
    ),
    KGS(
        "KGS",
        "kgs_id",
        KgsClient(),
        0.8
    ),
    OGS(
        "OGS",
        "ogs_id",
        OgsClient(),
        1.0
    ),
    FOX(
        "FOX",
        "fox_pseudo",
        FoxClient(),
        0.6
    ),
    IGS(
        "IGS",
        "igs_id",
        IgsClient(),
        0.6
    ),
    FFG(
        "FFG",
        "ffg_id",
        FfgClient(),
        0.6
    ),
    EGF(
        "EGF",
        "egf_id",
        EgfClient(),
        0.6
    );

    companion object {
        private val ACCOUNTS: List<UserAccount> = listOf(DISCORD, KGS, OGS, FOX, IGS, FFG, EGF)
        val SUPPORTED_PLAYABLE_ACCOUNTS = listOf(KGS, OGS, FOX)

        fun find(serverName: String): UserAccount? = ACCOUNTS.find { it.name == serverName }
    }
}
