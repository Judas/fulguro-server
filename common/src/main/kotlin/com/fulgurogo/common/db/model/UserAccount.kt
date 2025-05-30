package com.fulgurogo.common.db.model

enum class UserAccount(
    val fullName: String,
    val databaseId: String,
    val ratingWeight: Double
) {
    DISCORD(
        "Discord",
        "discord_id",
        0.0
    ),
    KGS(
        "KGS",
        "kgs_id",
        0.8
    ),
    OGS(
        "OGS",
        "ogs_id",
        1.0
    ),
    FOX(
        "FOX",
        "fox_pseudo",
        0.6
    ),
    IGS(
        "IGS",
        "igs_id",
        0.6
    ),
    FFG(
        "FFG",
        "ffg_id",
        0.6
    ),
    EGF(
        "EGF",
        "egf_id",
        0.6
    );

    companion object {
        private val ACCOUNTS: List<UserAccount> = listOf(DISCORD, KGS, OGS, FOX, IGS, FFG, EGF)
        val SUPPORTED_PLAYABLE_ACCOUNTS = listOf(KGS, OGS, FOX)

        fun find(serverName: String): UserAccount? = ACCOUNTS.find { it.name == serverName }
    }
}