package com.fulgurogo.api.db.model

/** Nullable for the same reason as [LinkRequestBody]: Gson does not honour Kotlin nullability. */
data class AuthRequestBody(
    val code: String?,
    val goldId: String?
)
