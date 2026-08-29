package com.fulgurogo.api.db.model

data class AdminLogsResponse(
    val lines: List<String>,
    val generatedAt: String,
)
