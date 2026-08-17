package com.fulgurogo.api.db.model

/**
 * Body of `POST /gold/api/house/choice`: `STAY`, `CHANGE` or `LEAVE`, parsed through `HouseAction.from`.
 *
 * Nullable for the reason [LinkRequestBody] gives — Gson ignores Kotlin nullability. [action] is a String rather than
 * the enum for the same reason: Gson maps an unknown enum name to null without a word, and an unknown action has to come
 * back as a 400.
 *
 * [slug] names the house a `CHANGE` goes to, by the same key as [HouseJoinRequestBody]. It is **required** on a
 * `CHANGE` — there is no draw to fall back on — and ignored on `STAY` and `LEAVE`, which have no destination. Ignored
 * rather than rejected, so a site that sends its whole form back on every action does not have to blank the field.
 *
 * Same absence of identity checking as [HouseJoinRequestBody], and it bites harder here: anyone can record a `LEAVE` for
 * anyone, or move them into any house. Accepted, and written down in the plan.
 */
data class HouseChoiceRequestBody(
    val discordId: String?,
    val action: String?,
    val slug: String?
)
