package com.fulgurogo.clean.db.model

/**
 * What a purge actually deleted, one entry per table.
 *
 * Keyed by table name rather than reduced to a total, because the admin who ran it has no other way to see the shape of
 * what went: a player whose Discord row `CleanService` had already taken purges as `house_points` alone, and that is the
 * case this endpoint exists for. An all-zero report is the honest answer to a mistyped id.
 */
data class PlayerPurgeReport(
    val discordId: String,
    val deleted: Map<String, Int>,
) {
    fun total(): Int = deleted.values.sum()
}
