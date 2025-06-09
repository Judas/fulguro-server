package com.fulgurogo.ogs.api.model

data class OgsUserList(val results: List<OgsUser> = mutableListOf())
data class OgsUser(val id: Int = 0)
