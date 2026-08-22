package com.fulgurogo.api.link

import com.fulgurogo.common.config.Config
import com.fulgurogo.kgs.db.KgsDatabaseAccessor
import com.fulgurogo.ogs.api.OgsApiClient
import com.fulgurogo.ogs.api.model.OgsUserList
import com.fulgurogo.ogs.db.OgsDatabaseAccessor

/**
 * Links one platform's accounts to Discord users.
 *
 * These live in the `api` module rather than in each platform module on purpose: `api` already depends on every
 * platform, and putting the interface anywhere else would force the platforms to depend back on `api`.
 */
data class ResolvedAccount(
    val id: String,
    val name: String? = null,
    val rank: String? = null,
)

interface AccountLinker {
    /** Platform name as the frontend knows it, e.g. `"KGS"`. */
    val server: String

    /**
     * Turns what the user typed into the id this platform stores, or null when no such account exists.
     * Most platforms store the id verbatim; OGS resolves a username to its numeric id.
     */
    fun resolveAccount(accountId: String): ResolvedAccount? = ResolvedAccount(accountId)

    /** Whether [accountId] is already linked to some Discord user. */
    fun isTaken(account: ResolvedAccount): Boolean

    /** Whether this Discord user already linked an account on this platform. */
    fun isLinked(discordId: String): Boolean = false

    /** Links [accountId] to [discordId]. Callers check [isTaken] first. */
    fun link(discordId: String, account: ResolvedAccount)
}

/** The supported linkers, keyed by [AccountLinker.server]. Iteration order is the order the frontend is served. */
class AccountLinkers(ogsApiClient: OgsApiClient, foxApiClient: FoxApiClient) {
    private val byServer: Map<String, AccountLinker> = listOf(
        KgsAccountLinker,
        OgsAccountLinker(ogsApiClient),
        FoxAccountLinker(foxApiClient)
    ).associateBy { it.server }

    val supportedServers: List<String> = byServer.keys.toList()

    operator fun get(server: String): AccountLinker? = byServer[server]
}

private object KgsAccountLinker : AccountLinker {
    override val server = "KGS"
    override fun isTaken(account: ResolvedAccount) = KgsDatabaseAccessor.user(account.id) != null
    override fun link(discordId: String, account: ResolvedAccount) = KgsDatabaseAccessor.addUser(discordId, account.id)
}

private class OgsAccountLinker(private val ogsApiClient: OgsApiClient) : AccountLinker {
    override val server = "OGS"

    /** OGS accounts are given as usernames but stored as numeric ids. */
    override fun resolveAccount(accountId: String): ResolvedAccount? {
        val url = "${Config.get("ogs.api.url")}/players?username=$accountId"
        return ogsApiClient.get(url, OgsUserList::class.java).results.firstOrNull()
            ?.let { ResolvedAccount(it.id.toString()) }
    }

    // ogs_id is the one platform id column that really is an INT
    override fun isTaken(account: ResolvedAccount) =
        account.id.toIntOrNull()?.let { OgsDatabaseAccessor.user(it) != null } ?: false

    override fun link(discordId: String, account: ResolvedAccount) =
        OgsDatabaseAccessor.addUser(discordId, account.id)
}

private class FoxAccountLinker(private val foxApiClient: FoxApiClient) : AccountLinker {
    override val server = "FOX"

    override fun resolveAccount(accountId: String): ResolvedAccount? = foxApiClient.findPlayer(accountId)?.let {
        ResolvedAccount(id = it.uid!!, name = it.username!!, rank = it.rank ?: "?")
    }

    override fun isTaken(account: ResolvedAccount) = FoxDatabaseAccessor.userByFoxId(account.id) != null
    override fun isLinked(discordId: String) = FoxDatabaseAccessor.userByDiscordId(discordId) != null
    override fun link(discordId: String, account: ResolvedAccount) =
        FoxDatabaseAccessor.addUser(discordId, account)
}
