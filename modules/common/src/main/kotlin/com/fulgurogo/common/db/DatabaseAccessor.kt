package com.fulgurogo.common.db

import com.fulgurogo.common.config.Config
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.sql2o.Connection
import org.sql2o.Sql2o
import org.sql2o.quirks.NoQuirks
import java.util.*

object DatabaseAccessor {
    private val dataSource: HikariDataSource = HikariDataSource(HikariConfig().apply {
        val port =
            if (Config.get("debug").toBoolean()) Config.get("ssh.forwarded.port").toInt()
            else Config.get("db.port").toInt()
        jdbcUrl =
            "jdbc:mysql://${Config.get("db.host")}:$port/${Config.get("db.name")}?useUnicode=true&characterEncoding=utf8"
        username = Config.get("db.user")
        password = Config.get("db.password")
        addDataSourceProperty("cachePrepStmts", "true")
        addDataSourceProperty("prepStmtCacheSize", "250")
        addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
        leakDetectionThreshold = 5000
    })

    val dao: Sql2o = Sql2o(dataSource, object : NoQuirks() {
        init {
            converters[Date::class.java] = CustomDateConverter()
        }
    }).apply {
        // MySQL column name => POJO variable name
        defaultColumnMappings = mapOf(
            "access_token" to "accessToken",
            "black_discord_avatar" to "blackDiscordAvatar",
            "black_discord_id" to "blackDiscordId",
            "black_discord_name" to "blackDiscordName",
            "black_id" to "blackId",
            "black_name" to "blackName",
            "black_rank" to "blackRank",
            "black_rating" to "blackRating",
            "black_tier_name" to "blackTierName",
            "black_tier_rank" to "blackTierRank",
            "discord_avatar" to "discordAvatar",
            "discord_id" to "discordId",
            "discord_name" to "discordName",
            "egf_id" to "egfId",
            "egf_name" to "egfName",
            "egf_rank" to "egfRank",
            "expiration_date" to "expirationDate",
            "ffg_id" to "ffgId",
            "ffg_name" to "ffgName",
            "ffg_rank" to "ffgRank",
            "fox_id" to "foxId",
            "fox_name" to "foxName",
            "fox_rank" to "foxRank",
            "gold_id" to "goldId",
            "gold_games" to "goldGames",
            "gold_ranked_games" to "goldRankedGames",
            "igs_id" to "igsId",
            "igs_rank" to "igsRank",
            "kgs_id" to "kgsId",
            "kgs_rank" to "kgsRank",
            "long_game" to "longGame",
            "ogs_id" to "ogsId",
            "ogs_name" to "ogsName",
            "ogs_rank" to "ogsRank",
            "refresh_token" to "refreshToken",
            "tier_rank" to "tierRank",
            "tier_name" to "tierName",
            "token_type" to "tokenType",
            "total_games" to "totalGames",
            "total_ranked_games" to "totalRankedGames",
            "white_discord_avatar" to "whiteDiscordAvatar",
            "white_discord_id" to "whiteDiscordId",
            "white_discord_name" to "whiteDiscordName",
            "white_id" to "whiteId",
            "white_name" to "whiteName",
            "white_rank" to "whiteRank",
            "white_rating" to "whiteRating",
            "white_tier_name" to "whiteTierName",
            "white_tier_rank" to "whiteTierRank"
        )
    }

    inline fun <T> withDao(block: (Connection) -> T): T = dao.open().use(block)
}
