package com.fulgurogo.fox.api.model

import com.google.gson.annotations.SerializedName
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.*

data class FoxApiGame(
    val id: Long = 0,
    val black: FoxApiUser,
    val white: FoxApiUser,
    @SerializedName("start_time") val started: String = "",
    @SerializedName("end_time") val ended: String = "",
    val settings: FoxApiSettings,
    val result: FoxApiResult
) {
    companion object {
        private const val DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss"
    }

    fun goldId(): String = "FOX_$id"
    fun date(): Date = try {
        SimpleDateFormat(DATE_FORMAT).parse(started, ParsePosition(0))
    } catch (_: Exception) {
        Date(0)
    }

    fun result(): String? = result.result()
}
