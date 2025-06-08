package com.fulgurogo.ogs.api.model

import com.google.gson.annotations.SerializedName

data class OgsAuthCredentials(
    @SerializedName("user_jwt") val jwt: String,
    @SerializedName("csrf_token") val csrf: String
)
