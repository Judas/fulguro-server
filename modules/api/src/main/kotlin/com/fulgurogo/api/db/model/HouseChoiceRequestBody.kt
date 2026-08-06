package com.fulgurogo.api.db.model

/**
 * Body of `POST /gold/api/house/choice`: `STAY`, `CHANGE` or `LEAVE`, parsed through `HouseAction.from`.
 *
 * Nullable for the reason [LinkRequestBody] gives — Gson ignores Kotlin nullability. [action] is a String rather than
 * the enum for the same reason: Gson maps an unknown enum name to null without a word, and an unknown action has to come
 * back as a 400.
 *
 * Same absence of identity checking as [HouseJoinRequestBody], and it bites harder here: anyone can record a `LEAVE` for
 * anyone. Accepted, and written down in the plan.
 */
data class HouseChoiceRequestBody(
    val discordId: String?,
    val action: String?
)
