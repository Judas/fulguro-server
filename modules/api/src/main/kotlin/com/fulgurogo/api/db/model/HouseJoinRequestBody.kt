package com.fulgurogo.api.db.model

/**
 * Body of `POST /gold/api/house/join`.
 *
 * Nullable for the reason [LinkRequestBody] gives: Gson does not honour Kotlin nullability and will leave a non-null
 * `String` holding null when the client omits the field.
 *
 * There is no proof of identity here, deliberately, exactly as with `POST /gold/api/link`: the id is taken as it comes.
 * Anyone can therefore join on someone else's behalf, into any house.
 *
 * [slug] is the house the player picked, by the same key the site already works from — `GET /gold/api/houses` lists them
 * and `GET /gold/api/house/{slug}` shows one. The internal house id stays out of the API here as everywhere else.
 */
data class HouseJoinRequestBody(
    val discordId: String?,
    val slug: String?
)
