package com.fulgurogo.api.db.model

import com.fulgurogo.common.utilities.GenerateNoArgConstructor

@GenerateNoArgConstructor
data class ApiPlayerAccount(
    val server: String? = null,
    val id: String? = null,
    val name: String? = null,
    val rank: String? = null,
    val link: String? = null
)
