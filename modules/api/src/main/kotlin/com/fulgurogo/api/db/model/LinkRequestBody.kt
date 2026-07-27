package com.fulgurogo.api.db.model

/**
 * Fields are nullable on purpose: Gson populates these by reflection and will happily leave a non-null Kotlin `String`
 * holding null when the client omits it. Declaring them non-null only moved the failure to the first dereference.
 */
data class LinkRequestBody(
    val discordId: String?,
    val account: String?,
    val accountId: String?
)
