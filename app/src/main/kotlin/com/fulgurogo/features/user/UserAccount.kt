package com.fulgurogo.features.user

import com.fulgurogo.features.user.egf.EgfClient
import com.fulgurogo.features.user.ffg.FfgClient
import com.fulgurogo.features.user.fox.FoxClient
import com.fulgurogo.features.user.igs.IgsClient
import com.fulgurogo.features.user.kgs.KgsClient
import com.fulgurogo.features.user.ogs.OgsClient
import net.dv8tion.jda.api.interactions.commands.Command

enum class UserAccount(
    val fullName: String,
    val databaseId: String,
    val help: String,
    val client: UserAccountClient
) {
    DISCORD(
        "Discord",
        "discord_id",
        "",
        NoOpUserAccountClient()
    ),
    KGS(
        "KGS",
        "kgs_id",
        "**KGS** : Ton id est ton pseudo",
        KgsClient()
    ),
    OGS(
        "OGS",
        "ogs_id",
        "**OGS** : Ton id est le chiffre visible dans l'URL de ta page profil : `https://online-go.com/player/XXXX`",
        OgsClient()
    ),
    FOX(
        "FOX",
        "fox_pseudo",
        "**FOX** : Ton id est ton pseudo",
        FoxClient()
    ),
    IGS(
        "IGS",
        "igs_id",
        "**IGS (Pandanet)** : Ton id est ton pseudo",
        IgsClient()
    ),
    FFG(
        "FFG",
        "ffg_id",
        "**FFG** : Ton id est le chiffre visible dans l'URL de ta page profil : `https://ffg.jeudego.org/php/affichePersonne.php?id=XXXX`",
        FfgClient()
    ),
    EGF(
        "EGF",
        "egf_id",
        "**EGF** : Ton id est le chiffre visible dans l'URL de ta page profil : `https://www.europeangodatabase.eu/EGD/Player_Card.php?key=XXXX`",
        EgfClient()
    );

    companion object {
        private val ACCOUNTS: List<UserAccount> = listOf(DISCORD, KGS, OGS, FOX, IGS, FFG, EGF)
        val LINKABLE_ACCOUNTS: List<UserAccount> = listOf(KGS, OGS, FOX, IGS, FFG, EGF)
        val LINKABLE_ACCOUNT_CHOICES: List<Command.Choice> =
            LINKABLE_ACCOUNTS.map { Command.Choice(it.name, it.name) }
        val SUPPORTED_PLAYABLE_ACCOUNTS = listOf(KGS, OGS, FOX)

        fun find(serverName: String): UserAccount? = ACCOUNTS.find { it.name == serverName }
    }
}
