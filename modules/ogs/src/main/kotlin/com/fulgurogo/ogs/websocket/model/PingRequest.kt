package com.fulgurogo.ogs.websocket.model

import java.time.Instant

data class PingRequest(val client: Long = Instant.now().epochSecond * 1000)
