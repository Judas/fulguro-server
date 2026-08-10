package com.fulgurogo.league

import com.fulgurogo.common.config.Config
import java.security.MessageDigest

/**
 * The one place a player's OGS league member id is computed.
 *
 * The id is ours — it goes in the URL of `PUT member/{id}` and OGS never hands one back — so it is derived rather than
 * drawn: `sha256(discordId + salt)`, truncated to 32 hex characters. Deterministic, so there is nothing to store,
 * nothing to read back before a call, and no column that can fall out of step with reality.
 *
 * **One implementation, and this is it.** A second one would be a second identity for the same player, and it would only
 * show up the day a match was created under it.
 *
 * The salt is not decoration, though its reason is narrower than it looks. The matches API answers 403 without the auth
 * headers, so member ids do not leak through it; what remains is OGS's own web pages, which can show what the API
 * protects. That is enough to keep it, because the attack is cheap and concrete: anyone on the FulguroGo Discord can
 * take the few hundred member ids of the server and hash the lot in a second to match them up. A salt closes that for
 * the price of a config key.
 *
 * ⚠ It also becomes a piece of data every player's OGS identity depends on. Changing it, or losing it, re-registers
 * everybody under new ids and detaches them from their OGS league history — and it lives in `config.properties`, which is
 * gitignored and therefore outside the database backups. Treat it like `bot.token`: it never changes, and it is backed up
 * with the server's other secrets.
 */
object LeagueMemberId {
    private const val SALT_KEY = "league.member.salt"

    /** 32 hex characters, so 128 bits. No collision to think about. */
    private const val LENGTH = 32

    private val salt: String by lazy { readSalt() }

    fun of(discordId: String): String = MessageDigest
        .getInstance("SHA-256")
        .digest((discordId + salt).toByteArray())
        .joinToString("") { "%02x".format(it) }
        .take(LENGTH)

    /**
     * Read once, and loudly.
     *
     * Read through [Config.getOrNull] and checked rather than through [Config.get], because the failure to avoid is not
     * a missing key — that would throw on its own — but an **empty** one. An empty salt hashes perfectly happily and
     * produces ids that look right and are wrong, and the mistake would only surface as players unable to find their
     * matches. Better to refuse to compute an id at all.
     */
    private fun readSalt(): String {
        val value = Config.getOrNull(SALT_KEY)?.trim()
        require(!value.isNullOrEmpty()) { "$SALT_KEY is missing or empty: OGS member ids cannot be computed" }
        return value
    }
}
