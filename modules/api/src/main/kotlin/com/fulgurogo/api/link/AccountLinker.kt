package com.fulgurogo.api.link

import com.fulgurogo.common.config.Config
import com.fulgurogo.fox.api.FoxApiClient
import com.fulgurogo.fox.db.FoxDatabaseAccessor
import com.fulgurogo.kgs.db.KgsDatabaseAccessor
import com.fulgurogo.ogs.api.OgsApiClient
import com.fulgurogo.ogs.api.model.OgsUserList
import com.fulgurogo.ogs.db.OgsDatabaseAccessor
import com.fulgurogo.discord.db.DiscordDatabaseAccessor
import com.fulgurogo.fgc.db.FgcDatabaseAccessor
import com.fulgurogo.gold.db.GoldDatabaseAccessor

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

    /** Removes exactly the association displayed to the admin. False means it changed or no longer exists. */
    fun unlink(discordId: String, accountId: String): Boolean
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

    fun hasLinkedAccount(discordId: String): Boolean = byServer.values.any { it.isLinked(discordId) }
}

enum class AccountUnlinkResult { REMOVED, UNKNOWN_SERVER, UNKNOWN_PLAYER, ASSOCIATION_NOT_FOUND }

fun interface AccountUnlinker {
    fun unlink(discordId: String, server: String, accountId: String): AccountUnlinkResult
}

class AccountUnlinkService(private val linkers: AccountLinkers) : AccountUnlinker {
    override fun unlink(discordId: String, server: String, accountId: String): AccountUnlinkResult {
        val linker = linkers[server] ?: return AccountUnlinkResult.UNKNOWN_SERVER
        if (DiscordDatabaseAccessor.user(discordId) == null) return AccountUnlinkResult.UNKNOWN_PLAYER
        if (!linker.unlink(discordId, accountId)) return AccountUnlinkResult.ASSOCIATION_NOT_FOUND

        FgcDatabaseAccessor.addPlayer(discordId)
        if (linkers.hasLinkedAccount(discordId)) GoldDatabaseAccessor.addPlayer(discordId)
        else GoldDatabaseAccessor.resetPlayer(discordId)
        return AccountUnlinkResult.REMOVED
    }
}

private object KgsAccountLinker : AccountLinker {
    override val server = "KGS"
    override fun isTaken(account: ResolvedAccount) = KgsDatabaseAccessor.user(account.id) != null
    override fun isLinked(discordId: String) = KgsDatabaseAccessor.userByDiscordId(discordId) != null
    override fun link(discordId: String, account: ResolvedAccount) = KgsDatabaseAccessor.addUser(discordId, account.id)
    override fun unlink(discordId: String, accountId: String) = KgsDatabaseAccessor.removeUser(discordId, accountId)
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

    override fun isLinked(discordId: String) = OgsDatabaseAccessor.userByDiscordId(discordId) != null

    override fun link(discordId: String, account: ResolvedAccount) =
        OgsDatabaseAccessor.addUser(discordId, account.id)
    override fun unlink(discordId: String, accountId: String) = OgsDatabaseAccessor.removeUser(discordId, accountId)
}

private class FoxAccountLinker(private val foxApiClient: FoxApiClient) : AccountLinker {
    override val server = "FOX"

    override fun resolveAccount(accountId: String): ResolvedAccount? = foxApiClient.findPlayer(accountId)?.let {
        ResolvedAccount(id = it.uid!!, name = it.username!!, rank = it.rank ?: "?")
    }

    override fun isTaken(account: ResolvedAccount) = FoxDatabaseAccessor.userByFoxId(account.id) != null
    override fun isLinked(discordId: String) = FoxDatabaseAccessor.userByDiscordId(discordId) != null
    override fun link(discordId: String, account: ResolvedAccount) =
        FoxDatabaseAccessor.addUser(discordId, account.id, account.name ?: "?", account.rank ?: "?")
    override fun unlink(discordId: String, accountId: String) = FoxDatabaseAccessor.removeUser(discordId, accountId)
}
