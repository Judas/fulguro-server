package com.fulgurogo.features.database

import com.fulgurogo.Config
import com.fulgurogo.Config.Ladder.INITIAL_DEVIATION
import com.fulgurogo.Config.Ladder.INITIAL_RATING
import com.fulgurogo.Config.Ladder.INITIAL_VOLATILITY
import com.fulgurogo.features.api.*
import com.fulgurogo.features.exam.*
import com.fulgurogo.features.fgc.FgcPlayer
import com.fulgurogo.features.games.Game
import com.fulgurogo.features.ladder.LadderPlayer
import com.fulgurogo.features.ladder.LadderRating
import com.fulgurogo.features.ladder.Tier
import com.fulgurogo.features.ladder.glicko.Glickotlin
import com.fulgurogo.features.user.User
import com.fulgurogo.features.user.UserAccount
import com.fulgurogo.features.user.UserAccount.Companion.SUPPORTED_PLAYABLE_ACCOUNTS
import com.fulgurogo.features.user.UserAccountGame
import com.fulgurogo.utilities.DATE_ZONE
import com.fulgurogo.utilities.Logger.Level.INFO
import com.fulgurogo.utilities.log
import com.fulgurogo.utilities.toDate
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.sql2o.Connection
import org.sql2o.Sql2o
import org.sql2o.quirks.NoQuirks
import java.time.ZonedDateTime
import java.util.*
import javax.sql.DataSource
import kotlin.math.roundToInt

object DatabaseAccessor {
    private val dataSource: DataSource = HikariDataSource(HikariConfig().apply {
        val port = if (Config.DEV) Config.SSH.FORWARDED_PORT else Config.Database.PORT
        jdbcUrl =
            "jdbc:mysql://${Config.Database.HOST}:$port/${Config.Database.NAME}?useUnicode=true&characterEncoding=utf8"
        username = Config.Database.USER
        password = Config.Database.PASSWORD
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
            "black_player_rating_gain" to "blackPlayerRatingGain",
            "black_current_rating" to "blackCurrentRating",
            "black_current_deviation" to "blackCurrentDeviation",
            "black_current_volatility" to "blackCurrentVolatility",
            "black_current_tier_rank" to "blackCurrentTierRank",
            "black_current_tier_name" to "blackCurrentTierName",
            "black_historical_rating" to "blackHistoricalRating",
            "black_historical_deviation" to "blackHistoricalDeviation",
            "black_historical_volatility" to "blackHistoricalVolatility",
            "black_historical_tier_rank" to "blackHistoricalTierRank",
            "black_historical_tier_name" to "blackHistoricalTierName",
            "white_player_discord_id" to "whitePlayerDiscordId",
            "white_name" to "whitePlayerName",
            "white_avatar" to "whitePlayerAvatar",
            "white_player_server_id" to "whitePlayerServerId",
            "white_player_pseudo" to "whitePlayerPseudo",
            "white_player_rank" to "whitePlayerRank",
            "white_player_won" to "whitePlayerWon",
            "white_player_rating_gain" to "whitePlayerRatingGain",
            "white_current_rating" to "whiteCurrentRating",
            "white_current_deviation" to "whiteCurrentDeviation",
            "white_current_volatility" to "whiteCurrentVolatility",
            "white_current_tier_rank" to "whiteCurrentTierRank",
            "white_current_tier_name" to "whiteCurrentTierName",
            "white_historical_rating" to "whiteHistoricalRating",
            "white_historical_deviation" to "whiteHistoricalDeviation",
            "white_historical_volatility" to "whiteHistoricalVolatility",
            "white_historical_tier_rank" to "whiteHistoricalTierRank",
            "white_historical_tier_name" to "whiteHistoricalTierName",
            "long_game" to "longGame",
            "rating_date" to "ratingDate",
            "bg_color" to "bgColor",
            "fg_color" to "fgColor",
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
        log(INFO, "user [$query] $accountId")
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
        log(INFO, "createUser [$query] $discordId")
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
                " egf_rank = :egfRank " +
                " WHERE ${UserAccount.DISCORD.databaseId} = :discordId "
        log(INFO, "updateUser [$query] ${user.discordId}")
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
            .executeUpdate()
    }

    fun updateSimpleUser(user: User): Connection = dao.open().use { connection ->
        val query = "UPDATE users SET " +
                " name = :name, " +
                " avatar = :avatar" +
                " WHERE ${UserAccount.DISCORD.databaseId} = :discordId "
        log(INFO, "updateSimpleUser [$query] ${user.discordId}")
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
        log(INFO, "updateUserScanDate [$query] $discordId")
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
            log(INFO, "linkUserAccount [$query] $accountId $discordId")
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
            log(INFO, "unlinkUserAccount [$query] $accountId $discordId")
            connection
                .createQuery(query)
                .addParameter("accountId", accountId)
                .addParameter("discordId", discordId)
                .executeUpdate()
        }

    fun deleteUser(discordId: String): Connection = dao.open().use { connection ->
        listOf(
            "exam_points",
            "ladder",
            "ladder_ratings",
            "users",
        ).forEach { table ->
            val query = "DELETE FROM $table WHERE ${UserAccount.DISCORD.databaseId} = :discordId"
            log(INFO, "deleteUser [$query] $discordId")
            connection.createQuery(query).addParameter("discordId", discordId).executeUpdate()
        }

        // Delete games
        val query = "DELETE FROM games " +
                " WHERE " +
                " (black_player_discord_id = :discordId AND white_player_discord_id IS NULL) " +
                " OR " +
                " (white_player_discord_id = :discordId AND black_player_discord_id IS NULL) "
        log(INFO, "deleteUser [$query] $discordId")
        connection.createQuery(query).addParameter("discordId", discordId).executeUpdate()
    }

    fun usersWithLinkedPlayableAccount(): List<User> = dao.open().use { connection ->
        val query = "SELECT * FROM users WHERE ${
            SUPPORTED_PLAYABLE_ACCOUNTS
                .map { "${it.databaseId} IS NOT NULL" }
                .reduce { a, b -> "$a OR $b" }
        }"
        log(INFO, "usersWithLinkedPlayableAccount [$query]")
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .executeAndFetch(User::class.java)
    }

    // endregion

    // region games

    fun existGame(game: UserAccountGame): Boolean = dao.open().use { connection ->
        val query = " SELECT * FROM games WHERE id = :id "

        log(INFO, "existGame [$query] ${game.gameId()}")
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

        log(INFO, "saveGame [$query] ${game.gameId()}")

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

        log(INFO, "unfinishedGamesFor [$query] $discordId")
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

        log(INFO, "updateFinishedGame [$query] ${game.gameId()}")
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

        log(INFO, "examGames [$query]")
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

        log(INFO, "ladderGamesFor [$query] $discordId from ($from) to ($to)")
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
            log(INFO, "countDailyGamesBetween [$query] $blackPlayerDiscordId $whitePlayerDiscordId $date")
            connection
                .createQuery(query)
                .throwOnMappingFailure(false)
                .addParameter("blackPlayerDiscordId", blackPlayerDiscordId)
                .addParameter("whitePlayerDiscordId", whitePlayerDiscordId)
                .addParameter("date", date)
                .executeScalar(Int::class.java) ?: 0
        }

    fun saveGameRatingGain(gameId: String, black: Boolean, offset: Double): Connection = dao.open().use { connection ->
        val query = " UPDATE games " +
                " SET ${if (black) "black_player_rating_gain" else "white_player_rating_gain"} = :ratingGain " +
                " WHERE id = :id "
        log(INFO, "saveGameRatingGain [$query] $gameId ${if (black) "black" else "white"} $offset")
        connection
            .createQuery(query)
            .addParameter("id", gameId)
            .addParameter("ratingGain", offset)
            .executeUpdate()
    }

    fun cleanGames(): Connection = dao.open().use { connection ->
        val query = "DELETE FROM games WHERE DATEDIFF(NOW(), date) > 32"
        log(INFO, "cleanGames [$query]")
        connection.createQuery(query).executeUpdate()
    }

    // endregion

    // region exam

    fun examPlayers(): MutableList<ExamPlayer> = dao.open().use { connection ->
        val query = "SELECT * FROM exam_points"
        log(INFO, "examPlayers [$query]")
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .executeAndFetch(ExamPlayer::class.java)
    }

    private fun examPlayer(user: User): ExamPlayer? = examPlayer(user.discordId)

    fun examPlayer(discordId: String): ExamPlayer? = dao.open().use { connection ->
        val query = "SELECT * FROM exam_points WHERE ${UserAccount.DISCORD.databaseId} = :discordId"
        log(INFO, "examPlayer [$query] $discordId")
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
        log(INFO, "createExamPlayer [$query] $user")
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

            log(INFO, "addExamPoints [$query] $discordId")
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
        log(INFO, "examStats [$query]")
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
        log(INFO, "titledHunters [$query]")
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .executeAndFetch(NamedExamPlayer::class.java)
    }

    fun getPromotions(): List<ExamAward> = dao.open().use { connection ->
        val query = "SELECT * FROM exam_awards ORDER BY id"
        log(INFO, "getPromotions [$query]")
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .executeAndFetch(ExamAward::class.java)
    }

    fun hasPromotionScore(promoName: String): Boolean = dao.open().use { connection ->
        val query = "SELECT * FROM exam_awards WHERE promo = :promoName"
        log(INFO, "hasPromotionScore [$query] $promoName")
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .addParameter("promoName", promoName)
            .executeAndFetchFirst(ExamAward::class.java) != null
    }

    fun savePromotionScore(promoName: String, stats: ExamSessionStats): Connection = dao.open().use { connection ->
        val query = "INSERT INTO exam_awards(promo, score, players, games, community_games) " +
                " VALUES (:promo, :score, :players, :games, :communityGames) "

        log(INFO, "savePromotionScore [$query] $promoName $stats")
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
        log(INFO, "examAward [$query]")
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .executeAndFetchFirst(ExamAward::class.java)
    }

    fun promoteHunter(examPlayer: ExamPlayer): Connection = dao.open().use { connection ->
        val query = "UPDATE exam_points SET hunter = 1 WHERE ${UserAccount.DISCORD.databaseId} = :discordId"
        log(INFO, "promoteExamPlayer [$query] ${examPlayer.discordId}")
        connection
            .createQuery(query)
            .addParameter("discordId", examPlayer.discordId)
            .executeUpdate()
    }

    fun resetSpec(specialization: ExamSpecialization): Connection = dao.open().use { connection ->
        val query = "UPDATE exam_points SET ${specialization.databaseId} = 0"
        log(INFO, "resetSpec [$query] ${specialization.name}")
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

            log(INFO, "incrementSpec [$updateQuery] ${hunter.discordId} ${specialization.name}")
            connection.createQuery(updateQuery).addParameter("discordId", hunter.discordId).executeUpdate()

            log(INFO, "capQuery [$capQuery] ${hunter.discordId}")
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

        log(INFO, "clearExamPoints [$query]")
        connection.createQuery(query).executeUpdate()
    }

    fun examRanking(): MutableList<ApiExamPlayer> = dao.open().use { connection ->
        val query = "SELECT * FROM exam_players " +
                " WHERE total > 0" +
                " ORDER BY total DESC, victory DESC, performance DESC, achievement DESC, refinement DESC, community DESC, patience DESC, participation DESC, ratio DESC, discord_id ASC "
        log(INFO, "examRanking [$query]")
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .executeAndFetch(ApiExamPlayer::class.java)
    }

    // endregion

    // region ladder

    fun ladderPlayers(): List<LadderPlayer> = dao.open().use { connection ->
        val query = "SELECT * FROM ladder"
        log(INFO, "ladderPlayers [$query]")
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .executeAndFetch(LadderPlayer::class.java)
    }

    fun ladderPlayer(user: User): LadderPlayer? = dao.open().use { connection ->
        val query = "SELECT * FROM ladder WHERE ${UserAccount.DISCORD.databaseId} = :discordId"
        log(INFO, "ladderPlayer [$query] ${user.discordId}")
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .addParameter("discordId", user.discordId)
            .executeAndFetchFirst(LadderPlayer::class.java)
    }

    fun createLadderPlayer(discordId: String): Connection = dao.open().use { connection ->
        val query = "INSERT INTO ladder( " +
                " ${UserAccount.DISCORD.databaseId}, " +
                " rating_date, " +
                " rating, " +
                " deviation, " +
                " volatility) " +
                " VALUES (:discordId, :ratingDate, :rating, :deviation, :volatility) "

        // 2 hours before creation to ensure games played right away are found
        val date = ZonedDateTime.now(DATE_ZONE).minusHours(2).toDate()

        log(INFO, "createLadderPlayer [$query] $discordId")
        connection
            .createQuery(query)
            .addParameter("discordId", discordId)
            .addParameter("ratingDate", date)
            .addParameter("rating", INITIAL_RATING)
            .addParameter("deviation", INITIAL_DEVIATION)
            .addParameter("volatility", INITIAL_VOLATILITY)
            .executeUpdate()
    }

    fun updateLadderPlayer(player: LadderPlayer): Connection = dao.open().use { connection ->
        val query = "UPDATE ladder SET " +
                " rating_date = :ratingDate, " +
                " rating = :rating, " +
                " deviation = :deviation, " +
                " volatility = :volatility, " +
                " ranked = :ranked " +
                " WHERE ${UserAccount.DISCORD.databaseId} = :discordId"

        log(INFO, "updateLadderPlayer [$query] $player")
        connection
            .createQuery(query)
            .addParameter("discordId", player.discordId)
            .addParameter("ratingDate", player.ratingDate)
            .addParameter("rating", player.rating)
            .addParameter("deviation", player.deviation)
            .addParameter("volatility", player.volatility)
            .addParameter("ranked", player.ranked)
            .executeUpdate()
    }

    fun updateLadderPlayerInitialRating(discordId: String, rating: Glickotlin.Player): Connection =
        dao.open().use { connection ->
            val query = "UPDATE ladder SET " +
                    " rating = :rating, " +
                    " deviation = :deviation, " +
                    " volatility = :volatility " +
                    " WHERE ${UserAccount.DISCORD.databaseId} = :discordId"

            log(INFO, "updateLadderPlayerInitialRating [$query] $discordId")
            connection
                .createQuery(query)
                .addParameter("discordId", discordId)
                .addParameter("rating", rating.rating())
                .addParameter("deviation", rating.deviation())
                .addParameter("volatility", rating.volatility())
                .executeUpdate()
        }

    fun saveLadderRating(player: LadderPlayer): Connection = dao.open().use { connection ->
        val query = "INSERT INTO ladder_ratings( " +
                " ${UserAccount.DISCORD.databaseId}, " +
                " rating_date, " +
                " rating, " +
                " deviation, " +
                " volatility) " +
                " VALUES (:discordId, :ratingDate, :rating, :deviation, :volatility) " +
                " ON DUPLICATE KEY UPDATE rating=:rating, deviation=:deviation, volatility=:volatility"

        log(INFO, "saveLadderRating [$query] $player")
        connection
            .createQuery(query)
            .addParameter("discordId", player.discordId)
            .addParameter("ratingDate", player.ratingDate)
            .addParameter("rating", player.rating)
            .addParameter("deviation", player.deviation)
            .addParameter("volatility", player.volatility)
            .executeUpdate()
    }

    fun ladderRatingAt(playerId: String, date: Date): LadderRating? = dao.open().use { connection ->
        val query = "SELECT " +
                " lr.rating AS rating, " +
                " lr.deviation AS deviation, " +
                " lr.volatility AS volatility, " +
                " t.rank AS tierRank, " +
                " t.name AS tierName " +
                " FROM ladder_ratings AS lr " +
                " INNER JOIN ladder_tiers AS t ON (t.min <= lr.rating AND lr.rating < t.max) " +
                " WHERE lr.${UserAccount.DISCORD.databaseId} = :discordId AND DATEDIFF(lr.rating_date, :ratingDate) < 0 " +
                " ORDER BY lr.rating_date DESC LIMIT 1 "

        log(INFO, "ladderRatingAt [$query] $playerId $date")
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .addParameter("discordId", playerId)
            .addParameter("ratingDate", date)
            .executeAndFetchFirst(LadderRating::class.java)
    }

    fun cleanLadderRatings(): Connection = dao.open().use { connection ->
        val query = "DELETE FROM ladder_ratings WHERE DATEDIFF(NOW(), rating_date) > 40"
        log(INFO, "cleanLadderRatings [$query]")
        connection.createQuery(query).executeUpdate()
    }

    // endregion

    // region ladder API

    fun apiLadderPlayers(): List<ApiPlayer> = dao.open().use { connection ->
        val query = "SELECT * FROM ladder_players"
        log(INFO, "apiLadderPlayers [$query]")
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .executeAndFetch(ApiPlayer::class.java)
    }

    fun apiLadderPlayer(discordId: String): ApiPlayer? = dao.open().use { connection ->
        val query = "SELECT * FROM ladder_players WHERE ${UserAccount.DISCORD.databaseId} = :discordId "
        log(INFO, "apiLadderPlayer [$query] $discordId")
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .addParameter("discordId", discordId)
            .executeAndFetchFirst(ApiPlayer::class.java)
    }

    fun apiLadderGame(gameId: String): Game? = dao.open().use { connection ->
        val query = "SELECT * FROM ladder_games " +
                " WHERE id = :gameId "
        log(INFO, "apiLadderGame [$query] $gameId")
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
        log(INFO, "apiLadderGamesFor [$query] $discordId")
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .addParameter("discordId", discordId)
            .executeAndFetch(Game::class.java)
    }

    fun apiLadderRecentGames(): List<Game> = dao.open().use { connection ->
        val query = "SELECT * FROM ladder_games ORDER BY date DESC LIMIT 20 "
        log(INFO, "apiLadderRecentGames [$query]")
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .executeAndFetch(Game::class.java)
    }

    fun stability(discordId: String): ApiStability = dao.open().use { connection ->
        log(INFO, "stability $discordId")

        val periodQuery = " SELECT period FROM ladder_stability_options "
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

        val deviation = apiLadderPlayer(discordId)?.deviation ?: INITIAL_DEVIATION

        ApiStability(total, ladderTotal, deviation.roundToInt(), period)
    }

    fun stability(): ApiStability? = dao.open().use { connection ->
        val query = " SELECT game_count, ladder_game_count, deviation, period " +
                " FROM ladder_stability_options "
        log(INFO, "stability [$query]")
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .executeAndFetchFirst(ApiStability::class.java)
    }

    fun tiers(): List<Tier> = dao.open().use { connection ->
        val query = " SELECT * FROM ladder_tiers "
        log(INFO, "tiers [$query]")
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .executeAndFetch(Tier::class.java)
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
            log(INFO, "saveAuthCredentials [$query] $goldId $authCredentials")
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
        log(INFO, "getAuthCredentials [$query] $goldId")
        connection
            .createQuery(query)
            .addParameter("goldId", goldId)
            .throwOnMappingFailure(false)
            .executeAndFetchFirst(AuthCredentials::class.java)
    }

    // endregion

    // region FGC

    fun fgcPlayers(): List<FgcPlayer> = dao.open().use { connection ->
        val query = "SELECT lp.discord_id, lp.name, lp.avatar, lp.rating, u.kgs_rank, u.ogs_rank " +
                " FROM ladder_players AS lp " +
                " INNER JOIN users AS u ON lp.discord_id = u.discord_id " +
                " WHERE lp.stable = 1 " +
                " ORDER BY lp.rating DESC"
        log(INFO, "fgcPlayers [$query]")
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .executeAndFetch(FgcPlayer::class.java)
    }

    // endregion
}
