package com.fulgurogo.ogs.websocket.model

import com.google.gson.Gson

data class Request(
    val command: String,
    val data: Any,
    val id: Int? = null
) {
    private val gson = Gson()
    override fun toString() = "[\"$command\", ${gson.toJson(data)}, $id]"
}
