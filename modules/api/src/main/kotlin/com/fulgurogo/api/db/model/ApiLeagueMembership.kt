package com.fulgurogo.api.db.model

import com.fulgurogo.league.db.model.LeagueMember

/**
 * What `POST /gold/api/league/join` answers: where the player now stands in the league.
 *
 * [registeredWithOgs] is the field worth having. Joining does not call OGS — that is left to the tick, so an inscription
 * cannot fail because OGS is momentarily down — so a fresh member is registered locally and not yet known to OGS. Since
 * the invitation links cannot exist before that call lands, this is what lets the site say "you are in, your first
 * challenge follows" rather than looking broken.
 *
 * The internal house id and the season's session list stay out: the site has its own routes for those.
 */
data class ApiLeagueMembership(
    val season: String,
    val active: Boolean,
    /** False while the tick still owes OGS a `PUT member/{id}` for this player. */
    val registeredWithOgs: Boolean
) {
    companion object {
        fun from(member: LeagueMember, registeredWithOgs: Boolean): ApiLeagueMembership = ApiLeagueMembership(
            season = member.season,
            active = member.active,
            registeredWithOgs = registeredWithOgs
        )
    }
}
