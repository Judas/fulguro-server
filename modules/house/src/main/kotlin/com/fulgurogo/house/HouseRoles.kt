package com.fulgurogo.house

import com.fulgurogo.common.config.Config
import com.fulgurogo.common.logger.log
import com.fulgurogo.discord.DiscordModule
import com.fulgurogo.house.HouseModule.TAG
import com.fulgurogo.house.db.HouseDatabaseAccessor
import com.fulgurogo.house.db.model.House

/**
 * The Discord role that goes with a membership: granted when a player lands in a house, taken back when they leave it.
 *
 * It lives beside [HouseNotifier] and for the same reason — the two are what a membership *looks like* from Discord, and
 * both have the same three callers: `POST /gold/api/house/join`, and the `CHANGE` and `LEAVE` intentions applied when a
 * season opens. Keeping the pairing in one place is what stops a fourth path from announcing an arrival without dressing
 * it, or the other way round.
 *
 * The four role ids come from `config.properties`, `house.role.<slug lowercased>`, which puts them where `bot.guild.id`
 * already is: a role id only means anything on one guild, and the dev and prod files already name different guilds. So a
 * local run hands out the test server's roles by construction, the same way it already posts to the test channel, and
 * neither depends on remembering to keep a database column in step with the config file next to it.
 *
 * Everything here is best-effort and says so by returning nothing. The database is the record of who is in which house;
 * a role is a decoration on top of it, so a failure to hand one out is a log line and never a failed join. The practical
 * consequence is that roles can drift from `house_members` — a member who joined while the bot was disconnected keeps
 * their row and not their role — and the only cure is to grant it again. That is a deliberate trade: the alternative,
 * refusing a join because Discord was briefly unreachable, is worse for the one thing that actually matters.
 */
object HouseRoles {
    /** Gives [discordId] the role of [house]. */
    fun grant(discordId: String, house: House) {
        val roleId = roleId(house) ?: return
        log(TAG, "Granting the ${house.slug} role to $discordId")
        DiscordModule.discordBot.addRole(discordId, roleId)
    }

    /** Takes the role of [house] away from [discordId]. */
    fun revoke(discordId: String, house: House) {
        val roleId = roleId(house) ?: return
        log(TAG, "Revoking the ${house.slug} role from $discordId")
        DiscordModule.discordBot.removeRole(discordId, roleId)
    }

    /**
     * Takes away the role of the house [houseId] names, for the callers that hold a `house_members` row rather than a
     * [House] — the season transition, which reads a member's old house off their membership.
     *
     * A house id that resolves to nothing means the row pointed at a house that no longer exists, which is worth the log
     * line: unlike a house with no configured role, it is a state nothing should produce.
     */
    fun revoke(discordId: String, houseId: Int) {
        val house = HouseDatabaseAccessor.house(houseId)
        if (house == null) {
            log(TAG, "Cannot revoke the role of $discordId: no house with id $houseId")
            return
        }
        revoke(discordId, house)
    }

    /**
     * The configured role of a house, or null when it has none.
     *
     * Read through [Config.getOrNull] and treating a blank value as no role at all, so that a house left unconfigured —
     * or a whole feature not rolled out yet — is a supported state rather than an NPE inside the `join` handler, where it
     * would turn a perfectly good join into a 500 over a decoration.
     *
     * The absence is logged, unlike the equivalent in [HouseNotifier]'s neighbourhood, because there is no reason for one
     * of the four to be missing: the keys ship in all three config files. A silent skip here is how "nobody gets a role"
     * survives a deploy unnoticed.
     */
    private fun roleId(house: House): String? {
        val roleId = Config.getOrNull("$ROLE_KEY_PREFIX${house.slug.lowercase()}")?.trim()
        if (roleId.isNullOrEmpty()) {
            log(TAG, "No Discord role configured for house ${house.slug}")
            return null
        }
        return roleId
    }

    /** Suffixed with the lowercased slug, which is the only stable name a house has. */
    private const val ROLE_KEY_PREFIX = "house.role."
}
