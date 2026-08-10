package com.fulgurogo.api.db.model

/**
 * Body of `POST /gold/api/league/join` and `POST /gold/api/league/leave`. One class for both, because both ask the same
 * single question and a second identical class would only be a second thing to keep in step.
 *
 * Nullable for the reason [LinkRequestBody] gives: Gson does not honour Kotlin nullability and will leave a non-null
 * `String` holding null when the client omits the field.
 *
 * No proof of identity, deliberately, exactly as with `POST /gold/api/link` and `POST /gold/api/house/join`: the id is
 * taken as it comes. ⚠ On `leave` that is sharper than anywhere else in this API — anyone can post a leave on somebody
 * else's id, and the victim loses the current session's draw and, with it, the perfect-attendance bonus for the season.
 * A `join` afterwards restores `active` without touching `joined`, so a sabotage spotted before the next draw costs
 * nothing; past that, the repair is by hand in the database.
 */
data class LeagueMembershipRequestBody(
    val discordId: String?
)
