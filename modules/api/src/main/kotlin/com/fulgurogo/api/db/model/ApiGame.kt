package com.fulgurogo.api.db.model

import com.fulgurogo.common.utilities.GenerateNoArgConstructor

@GenerateNoArgConstructor
data class ApiGame(
    val id: String,
    val date: String,
    val server: String,

    val gameLink: String? = null,
    val sgf: String? = null,

    val handicap: Int? = null,
    val komi: Double? = null,
    val longGame: Boolean? = null,
    val finished: Boolean,

//    val black: ApiGameParticipant,
//    val white: ApiGameParticipant
)
