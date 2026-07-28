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
interface AccountLinker {
    /** Platform name as the frontend knows it, e.g. `"KGS"`. */
    val server: String

    /**
     * Turns what the user typed into the id this platform stores, or null when no such account exists.
     * Most platforms store the id verbatim; OGS resolves a username to its numeric id.
     */
    fun resolveAccountId(accountId: String): String? = accountId

    /** Whether [accountId] is already linked to some Discord user. */
    fun isTaken(accountId: String): Boolean

    /** Links [accountId] to [discordId]. Callers check [isTaken] first. */
    fun link(discordId: String, accountId: String)
}

/** The supported linkers, keyed by [AccountLinker.server]. Iteration order is the order the frontend is served. */
class AccountLinkers(ogsApiClient: OgsApiClient) {
    private val byServer: Map<String, AccountLinker> = listOf(
        KgsAccountLinker,
        OgsAccountLinker(ogsApiClient)
    ).associateBy { it.server }

    val supportedServers: List<String> = byServer.keys.toList()

    operator fun get(server: String): AccountLinker? = byServer[server]
}

private object KgsAccountLinker : AccountLinker {
    override val server = "KGS"
    override fun isTaken(accountId: String) = KgsDatabaseAccessor.user(accountId) != null
    override fun link(discordId: String, accountId: String) = KgsDatabaseAccessor.addUser(discordId, accountId)
}

private class OgsAccountLinker(private val ogsApiClient: OgsApiClient) : AccountLinker {
    override val server = "OGS"

    /** OGS accounts are given as usernames but stored as numeric ids. */
    override fun resolveAccountId(accountId: String): String? {
        val url = "${Config.get("ogs.api.url")}/players?username=$accountId"
        return ogsApiClient.get(url, OgsUserList::class.java).results.firstOrNull()?.id?.toString()
    }

    // ogs_id is the one platform id column that really is an INT
    override fun isTaken(accountId: String) =
        accountId.toIntOrNull()?.let { OgsDatabaseAccessor.user(it) != null } ?: false

    override fun link(discordId: String, accountId: String) = OgsDatabaseAccessor.addUser(discordId, accountId)
}
