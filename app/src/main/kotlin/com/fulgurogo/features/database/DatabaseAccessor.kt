package com.fulgurogo.features.database

import com.fulgurogo.TAG
import com.fulgurogo.common.config.Config
import com.fulgurogo.common.db.CustomDateConverter
import com.fulgurogo.common.logger.log
import com.fulgurogo.common.utilities.DATE_ZONE
import com.fulgurogo.common.utilities.toDate
import com.fulgurogo.features.api.*
import com.fulgurogo.features.exam.*
import com.fulgurogo.features.games.Game
import com.fulgurogo.features.ladder.Tier
import com.fulgurogo.features.user.User
import com.fulgurogo.features.user.UserAccount
import com.fulgurogo.features.user.UserAccount.Companion.SUPPORTED_PLAYABLE_ACCOUNTS
import com.fulgurogo.features.user.UserAccountGame
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.sql2o.Connection
import org.sql2o.Sql2o
import org.sql2o.quirks.NoQuirks
import java.time.ZonedDateTime
import java.util.*
import javax.sql.DataSource

object DatabaseAccessor {
    private val dataSource: DataSource = HikariDataSource(HikariConfig().apply {
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
    })

    private val dao: Sql2o = Sql2o(dataSource, object : NoQuirks() {
        init {
            converters[Date::class.java] = CustomDateConverter()
        }
    }).apply {
        // MySQL column name => POJO variable name
        defaultColumnMappings = mapOf(
            "discord_id" to "discordId",
            "last_game_scan" to "lastGameScan",
            "kgs_id" to "kgsId",
            "kgs_pseudo" to "kgsPseudo",
            "kgs_rank" to "kgsRank",
            "ogs_id" to "ogsId",
            "ogs_pseudo" to "ogsPseudo",
            "ogs_rank" to "ogsRank",
            "fox_id" to "foxId",
            "fox_pseudo" to "foxPseudo",
            "fox_rank" to "foxRank",
            "igs_id" to "igsId",
            "igs_pseudo" to "igsPseudo",
            "igs_rank" to "igsRank",
            "ffg_id" to "ffgId",
            "ffg_pseudo" to "ffgPseudo",
            "ffg_rank" to "ffgRank",
            "egf_id" to "egfId",
            "egf_pseudo" to "egfPseudo",
            "egf_rank" to "egfRank",
            "black_player_discord_id" to "blackPlayerDiscordId",
            "black_name" to "blackPlayerName",
            "black_avatar" to "blackPlayerAvatar",
            "black_player_server_id" to "blackPlayerServerId",
            "black_player_pseudo" to "blackPlayerPseudo",
            "black_player_rank" to "blackPlayerRank",
            "black_player_won" to "blackPlayerWon",
            "black_rating" to "blackRating",
            "black_tier_rank" to "blackTierRank",
            "black_tier_name" to "blackTierName",
            "white_player_discord_id" to "whitePlayerDiscordId",
            "white_name" to "whitePlayerName",
            "white_avatar" to "whitePlayerAvatar",
            "white_player_server_id" to "whitePlayerServerId",
            "white_player_pseudo" to "whitePlayerPseudo",
            "white_player_rank" to "whitePlayerRank",
            "white_player_won" to "whitePlayerWon",
            "white_rating" to "whiteRating",
            "white_tier_rank" to "whiteTierRank",
            "white_tier_name" to "whiteTierName",
            "long_game" to "longGame",
            "tier_rank" to "tierRank",
            "tier_name" to "tierName",
            "game_count" to "gameCount",
            "ladder_game_count" to "ladderGameCount",
            "gold_id" to "goldId",
            "access_token" to "accessToken",
            "token_type" to "tokenType",
            "refresh_token" to "refreshToken",
            "expiration_date" to "expirationDate",
            "community_games" to "communityGames"
        )
    }

    // region user

    fun user(account: UserAccount, accountId: String): User? = dao.open().use { connection ->
        val query = "SELECT * FROM users WHERE ${account.databaseId} = :accountId"
        log(TAG, "user [$query] $accountId")
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .addParameter("accountId", accountId)
            .executeAndFetchFirst(User::class.java)
    }

    fun ensureUser(discordId: String): User = user(UserAccount.DISCORD, discordId)
        ?: run {
            createUser(discordId)
            user(UserAccount.DISCORD, discordId)!!
        }

    private fun createUser(discordId: String): Connection = dao.open().use { connection ->
        val query = "INSERT INTO users(${UserAccount.DISCORD.databaseId}) VALUES (:discordId) "
        log(TAG, "createUser [$query] $discordId")
        connection
            .createQuery(query)
            .addParameter("discordId", discordId)
            .executeUpdate()
    }

    fun updateUser(user: User): Connection = dao.open().use { connection ->
        val query = "UPDATE users SET " +
                " name = :name, " +
                " avatar = :avatar, " +
                " titles = :titles, " +
                " last_game_scan = :lastGameScan, " +
                " kgs_id = :kgsId, " +
                " kgs_pseudo = :kgsPseudo, " +
                " kgs_rank = :kgsRank, " +
                " ogs_id = :ogsId, " +
                " ogs_pseudo = :ogsPseudo, " +
                " ogs_rank = :ogsRank, " +
                " fox_id = :foxId, " +
                " fox_pseudo = :foxPseudo, " +
                " fox_rank = :foxRank, " +
                " igs_id = :igsId, " +
                " igs_pseudo = :igsPseudo, " +
                " igs_rank = :igsRank, " +
                " ffg_id = :ffgId, " +
                " ffg_pseudo = :ffgPseudo, " +
                " ffg_rank = :ffgRank, " +
                " egf_id = :egfId, " +
                " egf_pseudo = :egfPseudo, " +
                " egf_rank = :egfRank, " +
                " rating = :rating, " +
                " tier_rank = :tierRank, " +
                " tier_name = :tierName " +
                " WHERE ${UserAccount.DISCORD.databaseId} = :discordId "
        log(TAG, "updateUser [$query] ${user.discordId}")
        connection
            .createQuery(query)
            .addParameter("discordId", user.discordId)
            .addParameter("name", user.name)
            .addParameter("avatar", user.avatar)
            .addParameter("titles", user.titles)
            .addParameter("lastGameScan", user.lastGameScan)
            .addParameter("kgsId", user.kgsId)
            .addParameter("kgsPseudo", user.kgsPseudo)
            .addParameter("kgsRank", user.kgsRank)
            .addParameter("ogsId", user.ogsId)
            .addParameter("ogsPseudo", user.ogsPseudo)
            .addParameter("ogsRank", user.ogsRank)
            .addParameter("foxId", user.foxId)
            .addParameter("foxPseudo", user.foxPseudo)
            .addParameter("foxRank", user.foxRank)
            .addParameter("igsId", user.igsId)
            .addParameter("igsPseudo", user.igsPseudo)
            .addParameter("igsRank", user.igsRank)
            .addParameter("ffgId", user.ffgId)
            .addParameter("ffgPseudo", user.ffgPseudo)
            .addParameter("ffgRank", user.ffgRank)
            .addParameter("egfId", user.egfId)
            .addParameter("egfPseudo", user.egfPseudo)
            .addParameter("egfRank", user.egfRank)
            .addParameter("rating", user.rating)
            .addParameter("tierRank", user.tierRank)
            .addParameter("tierName", user.tierName)
            .executeUpdate()
    }

    fun updateSimpleUser(user: User): Connection = dao.open().use { connection ->
        val query = "UPDATE users SET " +
                " name = :name, " +
                " avatar = :avatar" +
                " WHERE ${UserAccount.DISCORD.databaseId} = :discordId "
        log(TAG, "updateSimpleUser [$query] ${user.discordId}")
        connection
            .createQuery(query)
            .addParameter("discordId", user.discordId)
            .addParameter("name", user.name)
            .addParameter("avatar", user.avatar)
            .executeUpdate()
    }

    fun updateUserScanDate(discordId: String, date: Date): Connection = dao.open().use { connection ->
        val query = "UPDATE users SET " +
                " last_game_scan = :lastGameScan " +
                " WHERE ${UserAccount.DISCORD.databaseId} = :discordId "
        log(TAG, "updateUserScanDate [$query] $discordId")
        connection
            .createQuery(query)
            .addParameter("discordId", discordId)
            .addParameter("lastGameScan", date)
            .executeUpdate()
    }

    fun linkUserAccount(discordId: String, account: UserAccount, accountId: String): Connection =
        dao.open().use { connection ->
            val query = "UPDATE users " +
                    " SET ${account.databaseId} = :accountId " +
                    " WHERE ${UserAccount.DISCORD.databaseId} = :discordId"
            log(TAG, "linkUserAccount [$query] $accountId $discordId")
            connection
                .createQuery(query)
                .addParameter("accountId", accountId)
                .addParameter("discordId", discordId)
                .executeUpdate()
        }

    fun unlinkUserAccount(discordId: String, account: UserAccount, accountId: String): Connection =
        dao.open().use { connection ->
            val query = "UPDATE users " +
                    " SET ${account.databaseId} = NULL " +
                    " WHERE ${UserAccount.DISCORD.databaseId} = :discordId"
            log(TAG, "unlinkUserAccount [$query] $accountId $discordId")
            connection
                .createQuery(query)
                .addParameter("accountId", accountId)
                .addParameter("discordId", discordId)
                .executeUpdate()
        }

    fun deleteUser(discordId: String): Connection = dao.open().use { connection ->
        listOf("exam_points", "users").forEach { table ->
            val query = "DELETE FROM $table WHERE ${UserAccount.DISCORD.databaseId} = :discordId"
            log(TAG, "deleteUser [$query] $discordId")
            connection.createQuery(query).addParameter("discordId", discordId).executeUpdate()
        }

        // Delete games
        val query = "DELETE FROM games " +
                " WHERE " +
                " (black_player_discord_id = :discordId AND white_player_discord_id IS NULL) " +
                " OR " +
                " (white_player_discord_id = :discordId AND black_player_discord_id IS NULL) "
        log(TAG, "deleteUser [$query] $discordId")
        connection.createQuery(query).addParameter("discordId", discordId).executeUpdate()
    }

    fun usersWithLinkedPlayableAccount(): List<User> = dao.open().use { connection ->
        val query = "SELECT * FROM users WHERE ${
            SUPPORTED_PLAYABLE_ACCOUNTS
                .map { "${it.databaseId} IS NOT NULL" }
                .reduce { a, b -> "$a OR $b" }
        }"
        log(TAG, "usersWithLinkedPlayableAccount [$query]")
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .executeAndFetch(User::class.java)
    }

    // endregion

    // region games

    fun existGame(game: UserAccountGame): Boolean = dao.open().use { connection ->
        val query = " SELECT * FROM games WHERE id = :id "

        log(TAG, "existGame [$query] ${game.gameId()}")
        connection
            .createQuery(query)
            .addParameter("id", game.gameId())
            .executeAndFetchFirst(Game::class.java) != null
    }

    fun saveGame(
        game: UserAccountGame,
        blackPlayerDiscordId: String?,
        whitePlayerDiscordId: String?,
        sgf: String?
    ): Connection = dao.open().use { connection ->
        val query = "INSERT INTO games( " +
                " id, date, server, " +
                " black_player_discord_id, black_player_server_id, black_player_pseudo, black_player_rank, black_player_won, " +
                " white_player_discord_id, white_player_server_id, white_player_pseudo, white_player_rank, white_player_won, " +
                " handicap, komi, long_game, finished, sgf) " +
                " VALUES (:id, :date, :server, " +
                " :blackPlayerDiscordId, :blackPlayerServerId, :blackPlayerPseudo, :blackPlayerRank, :blackPlayerWon, " +
                " :whitePlayerDiscordId, :whitePlayerServerId, :whitePlayerPseudo, :whitePlayerRank, :whitePlayerWon, " +
                " :handicap, :komi, :longGame, :finished, :sgf) "

        log(TAG, "saveGame [$query] ${game.gameId()}")

        connection
            .createQuery(query)
            .addParameter("id", game.gameId())
            .addParameter("date", game.date())
            .addParameter("server", game.server())
            .addParameter("blackPlayerDiscordId", blackPlayerDiscordId)
            .addParameter("blackPlayerServerId", game.blackPlayerServerId())
            .addParameter("blackPlayerPseudo", game.blackPlayerPseudo())
            .addParameter("blackPlayerRank", game.blackPlayerRank())
            .addParameter("blackPlayerWon", game.blackPlayerWon())
            .addParameter("whitePlayerDiscordId", whitePlayerDiscordId)
            .addParameter("whitePlayerServerId", game.whitePlayerServerId())
            .addParameter("whitePlayerPseudo", game.whitePlayerPseudo())
            .addParameter("whitePlayerRank", game.whitePlayerRank())
            .addParameter("whitePlayerWon", game.whitePlayerWon())
            .addParameter("handicap", game.handicap())
            .addParameter("komi", game.komi())
            .addParameter("longGame", game.isLongGame())
            .addParameter("finished", game.isFinished())
            .addParameter("sgf", sgf)
            .executeUpdate()
    }

    fun unfinishedGamesFor(discordId: String): List<Game> = dao.open().use { connection ->
        val query = " SELECT * " +
                " FROM games " +
                " WHERE black_player_discord_id = :playerId OR white_player_discord_id = :playerId " +
                " AND finished = 0 " +
                " ORDER BY date "

        log(TAG, "unfinishedGamesFor [$query] $discordId")
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .addParameter("playerId", discordId)
            .executeAndFetch(Game::class.java)
    }

    fun updateFinishedGame(game: UserAccountGame, sgf: String?): Connection = dao.open().use { connection ->
        val query = " UPDATE games SET " +
                " black_player_won = :blackPlayerWon, " +
                " white_player_won = :whitePlayerWon, " +
                " finished = 1, " +
                " sgf = :sgf " +
                " WHERE id = :id "

        log(TAG, "updateFinishedGame [$query] ${game.gameId()}")
        connection
            .createQuery(query)
            .addParameter("id", game.gameId())
            .addParameter("blackPlayerWon", game.blackPlayerWon())
            .addParameter("whitePlayerWon", game.whitePlayerWon())
            .addParameter("sgf", sgf)
            .executeUpdate()
    }

    fun examGames(from: Date, to: Date): List<Game> = dao.open().use { connection ->
        val query = " SELECT * " +
                " FROM games " +
                " WHERE " +
                " finished = 1 AND :from < date AND date < :to " +
                " ORDER BY date "

        log(TAG, "examGames [$query]")
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .addParameter("from", from)
            .addParameter("to", to)
            .executeAndFetch(Game::class.java)
    }

    fun ladderGamesFor(discordId: String, from: Date, to: Date): List<Game> = dao.open().use { connection ->
        val query = " SELECT * " +
                " FROM games " +
                " WHERE " +
                " ((black_player_discord_id = :playerId AND white_player_discord_id IS NOT NULL) OR " +
                " (white_player_discord_id = :playerId AND black_player_discord_id IS NOT NULL)) " +
                " AND finished = 1 AND handicap = 0 AND 6 < komi AND komi < 9 " +
                " AND :from < date AND date < :to " +
                " ORDER BY date "

        log(TAG, "ladderGamesFor [$query] $discordId from ($from) to ($to)")
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .addParameter("playerId", discordId)
            .addParameter("from", from)
            .addParameter("to", to)
            .executeAndFetch(Game::class.java)
    }

    fun countDailyGamesBetween(blackPlayerDiscordId: String, whitePlayerDiscordId: String, date: Date): Int =
        dao.open().use { connection ->
            val query = "SELECT COUNT(*) " +
                    " FROM games " +
                    " WHERE ((black_player_discord_id = :blackPlayerDiscordId AND white_player_discord_id = :whitePlayerDiscordId) " +
                    " OR (black_player_discord_id = :whitePlayerDiscordId AND white_player_discord_id = :blackPlayerDiscordId)) " +
                    " AND DATE(date) = DATE(:date) AND date <= :date "
            log(TAG, "countDailyGamesBetween [$query] $blackPlayerDiscordId $whitePlayerDiscordId $date")
            connection
                .createQuery(query)
                .throwOnMappingFailure(false)
                .addParameter("blackPlayerDiscordId", blackPlayerDiscordId)
                .addParameter("whitePlayerDiscordId", whitePlayerDiscordId)
                .addParameter("date", date)
                .executeScalar(Int::class.java) ?: 0
        }

    fun cleanGames(): Connection = dao.open().use { connection ->
        val query = "DELETE FROM games WHERE DATEDIFF(NOW(), date) > 32"
        log(TAG, "cleanGames [$query]")
        connection.createQuery(query).executeUpdate()
    }

    // endregion

    // region exam

    fun examPlayers(): MutableList<ExamPlayer> = dao.open().use { connection ->
        val query = "SELECT * FROM exam_points"
        log(TAG, "examPlayers [$query]")
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .executeAndFetch(ExamPlayer::class.java)
    }

    private fun examPlayer(user: User): ExamPlayer? = examPlayer(user.discordId)

    fun examPlayer(discordId: String): ExamPlayer? = dao.open().use { connection ->
        val query = "SELECT * FROM exam_points WHERE ${UserAccount.DISCORD.databaseId} = :discordId"
        log(TAG, "examPlayer [$query] $discordId")
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .addParameter("discordId", discordId)
            .executeAndFetchFirst(ExamPlayer::class.java)
    }

    fun ensureExamPlayer(user: User): ExamPlayer = examPlayer(user) ?: run {
        createExamPlayer(user)
        examPlayer(user)!!
    }

    private fun createExamPlayer(user: User): Connection = dao.open().use { connection ->
        val query = "INSERT INTO exam_points(${UserAccount.DISCORD.databaseId}) VALUES (:discordId) "
        log(TAG, "createExamPlayer [$query] $user")
        connection
            .createQuery(query)
            .addParameter("discordId", user.discordId)
            .executeUpdate()
    }

    fun addExamPoints(discordId: String?, points: ExamPoints?) {
        if (discordId == null || points == null) return

        dao.open().use { connection ->
            val query = "UPDATE exam_points SET " +
                    " participation = participation + :participation, " +
                    " community = community + :community, " +
                    " patience = patience + :patience, " +
                    " victory = victory + :victory, " +
                    " refinement = refinement + :refinement, " +
                    " performance = performance + :performance, " +
                    " achievement = achievement + :achievement " +
                    " WHERE ${UserAccount.DISCORD.databaseId} = :discordId"

            log(TAG, "addExamPoints [$query] $discordId")
            connection
                .createQuery(query)
                .addParameter("participation", points.participation)
                .addParameter("community", points.community)
                .addParameter("patience", points.patience)
                .addParameter("victory", points.victory)
                .addParameter("refinement", points.refinement)
                .addParameter("performance", points.performance)
                .addParameter("achievement", points.achievement)
                .addParameter("discordId", discordId)
                .executeUpdate()
        }
    }

    fun examStats(): ExamSessionStats = dao.open().use { connection ->
        val query = "SELECT " +
                " SUM(participation) AS totalParticipation, " +
                " SUM(community) AS totalCommunity, " +
                " COUNT(*) AS candidates, " +
                " (SUM(participation) + SUM(community) + SUM(patience) + SUM(victory) + SUM(refinement) + SUM(performance) + SUM(achievement)) AS promoTotal " +
                " FROM exam_points WHERE participation > 0"
        log(TAG, "examStats [$query]")
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .executeAndFetchFirst(ExamSessionStats::class.java)
    }

    fun titledHunters(): List<NamedExamPlayer> = dao.open().use { connection ->
        val query = " SELECT u.name, e.* " +
                " FROM users AS u " +
                " INNER JOIN exam_points AS e ON u.discord_id = e.discord_id " +
                " WHERE e.information > 0 OR e.lost > 0 " +
                " OR e.ruin > 0 OR e.treasure > 0 OR e.gourmet > 0 " +
                " OR e.beast > 0 OR e.blacklist > 0 OR e.head > 0 "
        log(TAG, "titledHunters [$query]")
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .executeAndFetch(NamedExamPlayer::class.java)
    }

    fun getPromotions(): List<ExamAward> = dao.open().use { connection ->
        val query = "SELECT * FROM exam_awards ORDER BY id"
        log(TAG, "getPromotions [$query]")
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .executeAndFetch(ExamAward::class.java)
    }

    fun hasPromotionScore(promoName: String): Boolean = dao.open().use { connection ->
        val query = "SELECT * FROM exam_awards WHERE promo = :promoName"
        log(TAG, "hasPromotionScore [$query] $promoName")
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .addParameter("promoName", promoName)
            .executeAndFetchFirst(ExamAward::class.java) != null
    }

    fun savePromotionScore(promoName: String, stats: ExamSessionStats): Connection = dao.open().use { connection ->
        val query = "INSERT INTO exam_awards(promo, score, players, games, community_games) " +
                " VALUES (:promo, :score, :players, :games, :communityGames) "

        log(TAG, "savePromotionScore [$query] $promoName $stats")
        connection
            .createQuery(query)
            .addParameter("promo", promoName)
            .addParameter("score", stats.promoTotal)
            .addParameter("players", stats.candidates)
            .addParameter("games", stats.gamesPlayed())
            .addParameter("communityGames", stats.internalGamesPlayed())
            .executeUpdate()
    }

    fun examAward(): ExamAward? = dao.open().use { connection ->
        val query = "SELECT * FROM exam_awards ORDER BY score DESC LIMIT 1"
        log(TAG, "examAward [$query]")
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .executeAndFetchFirst(ExamAward::class.java)
    }

    fun promoteHunter(examPlayer: ExamPlayer): Connection = dao.open().use { connection ->
        val query = "UPDATE exam_points SET hunter = 1 WHERE ${UserAccount.DISCORD.databaseId} = :discordId"
        log(TAG, "promoteExamPlayer [$query] ${examPlayer.discordId}")
        connection
            .createQuery(query)
            .addParameter("discordId", examPlayer.discordId)
            .executeUpdate()
    }

    fun resetSpec(specialization: ExamSpecialization): Connection = dao.open().use { connection ->
        val query = "UPDATE exam_points SET ${specialization.databaseId} = 0"
        log(TAG, "resetSpec [$query] ${specialization.name}")
        connection
            .createQuery(query)
            .executeUpdate()
    }

    fun incrementSpec(hunter: ExamPlayer, specialization: ExamSpecialization): Connection =
        dao.open().use { connection ->
            val updateQuery =
                "UPDATE exam_points SET ${specialization.databaseId} = ${specialization.databaseId} + 1 WHERE ${UserAccount.DISCORD.databaseId} = :discordId"
            val capQuery =
                "UPDATE exam_points SET ${specialization.databaseId} = 4 WHERE ${UserAccount.DISCORD.databaseId} = :discordId AND ${specialization.databaseId} > 4"

            log(TAG, "incrementSpec [$updateQuery] ${hunter.discordId} ${specialization.name}")
            connection.createQuery(updateQuery).addParameter("discordId", hunter.discordId).executeUpdate()

            log(TAG, "capQuery [$capQuery] ${hunter.discordId}")
            connection.createQuery(capQuery).addParameter("discordId", hunter.discordId).executeUpdate()
        }

    fun clearExamPoints(): Connection = dao.open().use { connection ->
        val query = "UPDATE exam_points SET " +
                "participation = 0, " +
                "community = 0, " +
                "patience = 0, " +
                "victory = 0, " +
                "refinement = 0, " +
                "performance = 0, " +
                "achievement = 0 "

        log(TAG, "clearExamPoints [$query]")
        connection.createQuery(query).executeUpdate()
    }

    fun examRanking(): MutableList<ApiExamPlayer> = dao.open().use { connection ->
        val query = "SELECT * FROM exam_players " +
                " WHERE total > 0" +
                " ORDER BY total DESC, victory DESC, performance DESC, achievement DESC, refinement DESC, community DESC, patience DESC, participation DESC, ratio DESC, discord_id ASC "
        log(TAG, "examRanking [$query]")
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .executeAndFetch(ApiExamPlayer::class.java)
    }

    // endregion

    // region ladder API

    fun apiLadderPlayers(): List<ApiPlayer> = dao.open().use { connection ->
        val query = "SELECT u.*, is_player_stable(u.discord_id) AS stable " +
                " FROM users AS u " +
                " WHERE u.rating IS NOT NULL " +
                " ORDER BY u.rating DESC "
        log(TAG, "apiLadderPlayers [$query]")
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .executeAndFetch(ApiPlayer::class.java)
    }

    fun apiLadderPlayer(discordId: String): ApiPlayer? = dao.open().use { connection ->
        val query = "SELECT u.*, is_player_stable(u.discord_id) AS stable " +
                " FROM users AS u " +
                " WHERE ${UserAccount.DISCORD.databaseId} = :discordId"
        log(TAG, "apiLadderPlayer [$query] $discordId")
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .addParameter("discordId", discordId)
            .executeAndFetchFirst(ApiPlayer::class.java)
    }

    fun apiLadderGame(gameId: String): Game? = dao.open().use { connection ->
        val query = "SELECT * FROM ladder_games " +
                " WHERE id = :gameId "
        log(TAG, "apiLadderGame [$query] $gameId")
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .addParameter("gameId", gameId)
            .executeAndFetchFirst(Game::class.java)
    }

    fun apiLadderGamesFor(discordId: String): List<Game> = dao.open().use { connection ->
        val query = "SELECT * FROM ladder_games " +
                " WHERE black_player_discord_id = :discordId OR white_player_discord_id = :discordId " +
                " ORDER BY date DESC LIMIT 10 "
        log(TAG, "apiLadderGamesFor [$query] $discordId")
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .addParameter("discordId", discordId)
            .executeAndFetch(Game::class.java)
    }

    fun apiLadderRecentGames(): List<Game> = dao.open().use { connection ->
        val query = "SELECT * FROM ladder_games ORDER BY date DESC LIMIT 20 "
        log(TAG, "apiLadderRecentGames [$query]")
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .executeAndFetch(Game::class.java)
    }

    fun fgcValidation(discordId: String): ApiFgcValidation = dao.open().use { connection ->
        log(TAG, "stability $discordId")

        val periodQuery = " SELECT period FROM fgc_validation "
        val period = connection
            .createQuery(periodQuery)
            .throwOnMappingFailure(false)
            .executeScalar(Int::class.java) ?: 30

        val totalQuery = " SELECT COUNT(*) " +
                " FROM games " +
                " WHERE (black_player_discord_id = :discordId OR white_player_discord_id = :discordId) " +
                " AND finished = 1 AND DATEDIFF(NOW(), date) <= :period "
        val total = connection
            .createQuery(totalQuery)
            .throwOnMappingFailure(false)
            .addParameter("discordId", discordId)
            .addParameter("period", period)
            .executeScalar(Int::class.java) ?: 0

        val ladderTotalQuery = " SELECT COUNT(*) " +
                " FROM games " +
                " WHERE black_player_discord_id IS NOT NULL AND white_player_discord_id IS NOT NULL " +
                " AND (black_player_discord_id = :discordId OR white_player_discord_id = :discordId) " +
                " AND finished = 1 AND DATEDIFF(NOW(), date) <= :period " +
                " AND handicap = 0 AND 6 <= komi AND komi <= 9 "
        val ladderTotal = connection
            .createQuery(ladderTotalQuery)
            .throwOnMappingFailure(false)
            .addParameter("discordId", discordId)
            .addParameter("period", period)
            .executeScalar(Int::class.java) ?: 0

        ApiFgcValidation(total, ladderTotal, period)
    }

    fun fgcValidation(): ApiFgcValidation? = dao.open().use { connection ->
        val query = " SELECT game_count, ladder_game_count, period FROM fgc_validation "
        log(TAG, "fgcValidation [$query]")
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .executeAndFetchFirst(ApiFgcValidation::class.java)
    }

    fun tiers(): List<Tier> = dao.open().use { connection ->
        val query = " SELECT * FROM ladder_tiers "
        log(TAG, "tiers [$query]")
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .executeAndFetch(Tier::class.java)
    }

    fun tierForRating(rating: Double): Tier? = dao.open().use { connection ->
        val query = " SELECT * FROM ladder_tiers WHERE rank = tierRankFor(:rating) "
        log(TAG, "tierForRating [$query] $rating")
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .addParameter("rating", rating)
            .executeAndFetchFirst(Tier::class.java)
    }


    fun saveAuthCredentials(goldId: String, authCredentials: AuthRequestResponse): Connection =
        dao.open().use { connection ->
            val query =
                "INSERT INTO auth_credentials(gold_id, access_token, token_type, refresh_token, expiration_date) " +
                        " VALUES (:gold_id, :access_token, :token_type, :refresh_token, :expiration_date) " +
                        " ON DUPLICATE KEY UPDATE " +
                        " access_token=VALUES(access_token), " +
                        " token_type=VALUES(token_type), " +
                        " refresh_token=VALUES(refresh_token), " +
                        " expiration_date=VALUES(expiration_date)"
            log(TAG, "saveAuthCredentials [$query] $goldId $authCredentials")
            connection
                .createQuery(query)
                .addParameter("gold_id", goldId)
                .addParameter("access_token", authCredentials.access_token)
                .addParameter("token_type", authCredentials.token_type)
                .addParameter("refresh_token", authCredentials.refresh_token)
                .addParameter(
                    "expiration_date",
                    ZonedDateTime.now(DATE_ZONE).plusSeconds(authCredentials.expires_in).toDate()
                )
                .executeUpdate()
        }

    fun getAuthCredentials(goldId: String): AuthCredentials? = dao.open().use { connection ->
        val query = " SELECT * FROM auth_credentials WHERE gold_id = :goldId"
        log(TAG, "getAuthCredentials [$query] $goldId")
        connection
            .createQuery(query)
            .addParameter("goldId", goldId)
            .throwOnMappingFailure(false)
            .executeAndFetchFirst(AuthCredentials::class.java)
    }

    // endregion
}
