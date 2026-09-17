package com.fulgurogo.api.admin

import com.fulgurogo.clean.db.CleanDatabaseAccessor
import com.fulgurogo.clean.db.model.PlayerPurgeReport

/**
 * Removes a banned player from the ladder: their account, their house points and their academy.
 *
 * A `fun interface` for the same reason `AccountUnlinker` is one — it is the seam the route's tests are written against,
 * so they can exercise the four answers without a database.
 *
 * There is no "unknown player" answer, and that is a choice rather than an omission. The case this exists for is
 * precisely the one where the Discord row has *already* gone: `CleanService` takes it a day after Discord confirms the
 * departure, while `house_points` survives by design, and the result is a banned player whose points go on counting for
 * their house while they appear in no member listing at all. Refusing on a missing Discord row would refuse exactly
 * then. The report's counts are what tell a real purge from a mistyped id.
 */
fun interface PlayerPurger {
    fun purge(discordId: String): PlayerPurgeReport
}

class PlayerPurgeService : PlayerPurger {
    override fun purge(discordId: String): PlayerPurgeReport = CleanDatabaseAccessor.purgePlayer(discordId)
}
