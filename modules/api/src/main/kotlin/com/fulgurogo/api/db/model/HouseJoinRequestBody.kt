package com.fulgurogo.api.db.model

/**
 * Body of `POST /gold/api/house/join`.
 *
 * Nullable for the reason [LinkRequestBody] gives: Gson does not honour Kotlin nullability and will leave a non-null
 * `String` holding null when the client omits the field.
 *
 * There is no proof of identity here, deliberately, exactly as with `POST /gold/api/link`: the id is taken as it comes.
 * Anyone can therefore join on someone else's behalf.
 */
data class HouseJoinRequestBody(
    val discordId: String?
)
