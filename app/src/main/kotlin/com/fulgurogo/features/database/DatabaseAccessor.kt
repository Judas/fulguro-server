package com.fulgurogo.features.database

import com.fulgurogo.Config
import com.fulgurogo.features.exam.*
import com.fulgurogo.features.games.Game
import com.fulgurogo.features.ladder.LadderPlayer
import com.fulgurogo.features.ladder.LadderRating
import com.fulgurogo.features.ladder.api.ApiLadderPlayer
import com.fulgurogo.features.ladder.glicko.Glickotlin
import com.fulgurogo.features.league.LeaguePairing
import com.fulgurogo.features.user.User
import com.fulgurogo.features.user.UserAccount
import com.fulgurogo.features.user.UserAccount.Companion.SUPPORTED_PLAYABLE_ACCOUNTS
import com.fulgurogo.features.user.UserAccountGame
import com.fulgurogo.utilities.InvalidUserException
import com.fulgurogo.utilities.Logger.Level.INFO
import com.fulgurogo.utilities.log
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.sql2o.Connection
import org.sql2o.Sql2o
import org.sql2o.quirks.NoQuirks
import java.util.*
import javax.sql.DataSource

object DatabaseAccessor {
    private val dataSource: DataSource = HikariDataSource(HikariConfig().apply {
        val port = if (Config.DEV) Config.SSH.FORWARDED_PORT else Config.Database.PORT
        jdbcUrl =
            "jdbc:mysql://${Config.Database.HOST}:$port/${Config.Database.NAME}?useUnicode=true&characterEncoding=utf8"
        username = Config.Database.USER
        password = Config.Database.PASSWORD
        addDataSourceProperty("cachePrepStmts", "true");
        addDataSourceProperty("prepStmtCacheSize", "250");
        addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
    })

    private val dao: Sql2o = Sql2o(dataSource, object : NoQuirks() {
        init {
            converters[Date::class.java] = CustomDateConverter()
        }
    }).apply {
        // MySQL column name => POJO variable name
        defaultColumnMappings = mapOf(
            "discord_id" to "discordId",
            "kgs_id" to "kgsId",
            "kgs_pseudo" to "kgsPseudo",
            "ogs_id" to "ogsId",
            "ogs_pseudo" to "ogsPseudo",
            "fox_id" to "foxId",
            "fox_pseudo" to "foxPseudo",
            "igs_id" to "igsId",
            "ffg_id" to "ffgId",
            "egf_id" to "egfId",
            "last_game_scan" to "lastGameScan",
            "rating_date" to "ratingDate",
            "server_id" to "serverId",
            "main_player_id" to "mainPlayerId",
            "main_player_server_rank" to "mainPlayerServerRank",
            "main_player_rating_gain" to "mainPlayerRatingGain",
            "opponent_id" to "opponentId",
            "opponent_server_id" to "opponentServerId",
            "opponent_server_pseudo" to "opponentServerPseudo",
            "opponent_server_rank" to "opponentServerRank",
            "main_player_is_black" to "mainPlayerIsBlack",
            "main_player_won" to "mainPlayerWon",
            "long_game" to "longGame",
            "first_player_id" to "firstPlayerId",
            "second_player_id" to "secondPlayerId",
            "game_id" to "gameId",
            "winner_id" to "winnerId",
        )
    }

    // region user

    fun user(account: UserAccount, accountId: String): User? =
        dao.open().use { connection ->
            val query = "SELECT * FROM users WHERE ${account.databaseId} = :accountId"
            log(INFO, "user [$query] $accountId")
            connection
                .createQuery(query)
                .throwOnMappingFailure(false)
                .addParameter("accountId", accountId)
                .executeAndFetchFirst(User::class.java)
        }

    fun user(discordId: String): User? = dao.open().use { connection ->
        val query = "SELECT * FROM users WHERE ${UserAccount.DISCORD.databaseId} = :discordId"
        log(INFO, "user [$query] $discordId")
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .addParameter("discordId", discordId)
            .executeAndFetchFirst(User::class.java)
    }

    fun ensureUser(discordUser: net.dv8tion.jda.api.entities.User): User = ensureUser(discordUser.id)
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
                " kgs_id = :kgsId, " +
                " kgs_pseudo = :kgsPseudo, " +
                " ogs_id = :ogsId, " +
                " ogs_pseudo = :ogsPseudo," +
                " fox_id = :foxId, " +
                " fox_pseudo = :foxPseudo," +
                " igs_id = :igsId, " +
                " ffg_id = :ffgId," +
                " egf_id = :egfId, " +
                " titles = :titles, " +
                " last_game_scan = :lastGameScan " +
                " WHERE ${UserAccount.DISCORD.databaseId} = :discordId "
        log(INFO, "updateUser [$query] ${user.discordId}")
        connection
            .createQuery(query)
            .addParameter("discordId", user.discordId)
            .addParameter("name", user.name)
            .addParameter("avatar", user.avatar)
            .addParameter("kgsId", user.kgsId)
            .addParameter("kgsPseudo", user.kgsPseudo)
            .addParameter("ogsId", user.ogsId)
            .addParameter("ogsPseudo", user.ogsPseudo)
            .addParameter("foxId", user.foxId)
            .addParameter("foxPseudo", user.foxPseudo)
            .addParameter("igsId", user.igsId)
            .addParameter("ffgId", user.ffgId)
            .addParameter("egfId", user.egfId)
            .addParameter("titles", user.titles)
            .addParameter("lastGameScan", user.lastGameScan)
            .executeUpdate()
    }

    fun updateUserCheckDate(discordId: String, date: Date): Connection = dao.open().use { connection ->
        val query = "UPDATE users SET " +
                " last_game_scan = :lastGameScan " +
                " WHERE ${UserAccount.DISCORD.databaseId} = :discordId "
        log(INFO, "updateUserCheckDate [$query] $discordId")
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

    fun deleteUser(discordId: String) = dao.open().use { connection ->
        listOf(
            "exam_phantoms",
            "exam",
            "ladder",
            "ladder_ratings",
            "users",
        ).forEach { table ->
            val query = "DELETE FROM $table WHERE ${UserAccount.DISCORD.databaseId} = :discordId"
            log(INFO, "deleteUser [$query] $discordId")
            connection.createQuery(query).addParameter("discordId", discordId).executeUpdate()
        }
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

    fun saveGame(game: UserAccountGame): Connection = dao.open().use { connection ->
        val mainDiscordId = user(game.account(), game.mainPlayerAccountId())?.discordId ?: throw InvalidUserException
        val opponentDiscordId = user(game.account(), game.opponentAccountId())?.discordId

        val query = "INSERT INTO games( " +
                " date, " +
                " server, " +
                " server_id, " +
                " main_player_id, " +
                " main_player_server_rank, " +
                " opponent_id, " +
                " opponent_server_id, " +
                " opponent_server_pseudo, " +
                " opponent_server_rank, " +
                " main_player_is_black, " +
                " main_player_won, " +
                " handicap, " +
                " komi, " +
                " long_game, " +
                " finished) " +
                " VALUES (:date, :server, :serverId, :mainPlayerId, :mainPlayerServerRank, " +
                " :opponentId, :opponentServerId, :opponentServerPseudo, :opponentServerRank, " +
                " :mainPlayerIsBlack, :mainPlayerWon, :handicap, :komi, :longGame, :finished) "

        log(INFO, "saveGame [$query] $game")

        val victory =
            if (game.isFinished() && game.isWin()) true
            else if (game.isFinished() && game.isLoss()) false
            else null

        connection
            .createQuery(query)
            .addParameter("date", game.date())
            .addParameter("server", game.server())
            .addParameter("serverId", game.gameServerId())
            .addParameter("mainPlayerId", mainDiscordId)
            .addParameter("mainPlayerServerRank", game.mainPlayerRank())
            .addParameter("opponentId", opponentDiscordId)
            .addParameter("opponentServerId", game.opponentAccountId())
            .addParameter("opponentServerPseudo", game.opponentPseudo())
            .addParameter("opponentServerRank", game.opponentRank())
            .addParameter("mainPlayerIsBlack", game.isBlack())
            .addParameter("mainPlayerWon", victory)
            .addParameter("handicap", game.handicap())
            .addParameter("komi", game.komi())
            .addParameter("longGame", game.isLongGame())
            .addParameter("finished", game.isFinished())
            .executeUpdate()
    }

    fun existGame(game: UserAccountGame): Boolean = dao.open().use { connection ->
        val mainDiscordId = user(game.account(), game.mainPlayerAccountId())?.discordId ?: throw InvalidUserException
        val query = " SELECT * FROM games WHERE server_id = :serverId AND main_player_id = :mainPlayerId"

        log(INFO, "existGame [$query]")
        connection
            .createQuery(query)
            .addParameter("serverId", game.gameServerId())
            .addParameter("mainPlayerId", mainDiscordId)
            .executeAndFetchFirst(Game::class.java) != null
    }

    fun updateFinishedGame(user: User, gameId: Int, game: UserAccountGame): Connection = dao.open().use { connection ->
        val query = " UPDATE games SET main_player_won = :mainPlayerWon, finished = 1 WHERE id = :id "

        val victory =
            if (game.isFinished() && game.isWin()) true
            else if (game.isFinished() && game.isLoss()) false
            else null

        log(INFO, "updateFinishedGame [$query]")
        connection
            .createQuery(query)
            .addParameter("id", gameId)
            .addParameter("mainPlayerWon", victory)
            .executeUpdate()

        // Update ladder rating date if needed
        val dateQuery =
            " UPDATE ladder SET rating_date = LEAST(rating_date, :date) WHERE ${UserAccount.DISCORD.databaseId} = :discordId"

        log(INFO, "updateFinishedGame [$dateQuery]")
        connection
            .createQuery(dateQuery)
            .addParameter("discordId", user.discordId)
            .addParameter("date", game.date())
            .executeUpdate()
    }

    private fun games(): List<Game> = dao.open().use { connection ->
        val query = " SELECT * " +
                " FROM games " +
                " ORDER BY date "

        log(INFO, "games [$query]")
        connection
            .createQuery(query)
            .executeAndFetch(Game::class.java)
    }

    fun gamesFor(discordId: String): List<Game> = dao.open().use { connection ->
        val query = " SELECT * " +
                " FROM games " +
                " WHERE main_player_id = :mainPlayerId " +
                " ORDER BY date "

        log(INFO, "gamesFor [$query]")
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .addParameter("mainPlayerId", discordId)
            .executeAndFetch(Game::class.java)
    }

    fun infoGamesFor(discordId: String): List<Game> = gamesFor(discordId)
        .asSequence()
        .filter { it.finished }
        .filter { it.opponentServerPseudo != null }
        .toList()

    fun examGames(from: Date, to: Date): List<Game> = games()
        .filter { it.date.after(from) }
        .filter { it.date.before(to) }
        .filter { it.finished }

    fun ladderGames(): List<Game> = games()
        .asSequence()
        .filter { it.finished }
        .filter { it.opponentId != null }
        .filter { it.longGame == true }
        .filter { it.hasStandardHandicap() }
        .toList()

    fun ladderGamesFor(discordId: String, from: Date, to: Date): List<Game> = gamesFor(discordId)
        .asSequence()
        .filter { it.date.after(from) }
        .filter { it.date.before(to) }
        .filter { it.finished }
        .filter { it.opponentId != null }
        .filter { it.longGame == true }
        .filter { it.hasStandardHandicap() }
        .toList()

    fun oldestLadderGameId(): Int = dao.open().use { connection ->
        val query = " SELECT MIN(id) FROM games "
        log(INFO, "oldestLadderGameId [$query]")
        connection
            .createQuery(query)
            .executeScalar(Int::class.java) ?: 0
    }

    fun countDayGamesBetween(mainPlayerId: String, opponentId: String, date: Date): Int = dao.open().use { connection ->
        val query = "SELECT COUNT(*) " +
                " FROM games " +
                " WHERE main_player_id = :mainPlayerId AND opponent_id = :opponentId " +
                " AND DATE(date) = DATE(:date) AND date <= :date "
        log(INFO, "countDayGamesBetween [$query] $mainPlayerId $opponentId $date")
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .addParameter("mainPlayerId", mainPlayerId)
            .addParameter("opponentId", opponentId)
            .addParameter("date", date)
            .executeScalar(Int::class.java) ?: 0
    }

    fun saveGameRatingGain(gameId: Int, offset: Double): Connection = dao.open().use { connection ->
        val query = " UPDATE games SET main_player_rating_gain = :mainPlayerRatingGain WHERE id = :id "
        log(INFO, "saveGameRatingGain [$query] $gameId $offset")
        connection
            .createQuery(query)
            .addParameter("id", gameId)
            .addParameter("mainPlayerRatingGain", offset)
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
        val query = "SELECT * FROM exam"
        log(INFO, "examPlayers [$query]")
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .executeAndFetch(ExamPlayer::class.java)
    }

    private fun examPlayer(user: User): ExamPlayer? = examPlayer(user.discordId)

    fun examPlayer(discordId: String): ExamPlayer? = dao.open().use { connection ->
        val query = "SELECT * FROM exam WHERE ${UserAccount.DISCORD.databaseId} = :discordId"
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
        val query = "INSERT INTO exam(${UserAccount.DISCORD.databaseId}) VALUES (:discordId) "
        log(INFO, "createExamPlayer [$query] $user")
        connection
            .createQuery(query)
            .addParameter("discordId", user.discordId)
            .executeUpdate()
    }

    fun addExamGame(discordId: String, points: ExamPoints): Connection = dao.open().use { connection ->
        val query = "UPDATE exam SET " +
                " participation = participation + :participation, " +
                " community = community + :community, " +
                " patience = patience + :patience, " +
                " victory = victory + :victory, " +
                " refinement = refinement + :refinement, " +
                " performance = performance + :performance, " +
                " achievement = achievement + :achievement " +
                " WHERE ${UserAccount.DISCORD.databaseId} = :discordId"

        log(INFO, "addExamGame [$query] $discordId")
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

    fun revealExamPhantom(revealerId: String, phantomId: String): Connection = dao.open().use { connection ->
        val queryUpdate = "UPDATE exam_phantoms SET " +
                " revealed = 1, " +
                " revealer = :revealerId " +
                " WHERE ${UserAccount.DISCORD.databaseId} = :phantomId "
        log(INFO, "revealExamPhantom [$queryUpdate] $revealerId $phantomId")
        connection
            .createQuery(queryUpdate)
            .addParameter("revealerId", revealerId)
            .addParameter("phantomId", phantomId)
            .executeUpdate()

        val queryPoints = "UPDATE exam SET phantom = phantom + :phantom " +
                " WHERE ${UserAccount.DISCORD.databaseId} = :revealerId "
        log(INFO, "revealExamPhantom [$queryPoints] $revealerId")
        connection
            .createQuery(queryPoints)
            .addParameter("revealerId", revealerId)
            .addParameter("phantom", 20)
            .executeUpdate()
    }

    fun examPhantoms(): List<ExamPhantom> = dao.open().use { connection ->
        val query = "SELECT * FROM exam_phantoms"
        log(INFO, "examPhantoms [$query]")
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .executeAndFetch(ExamPhantom::class.java)
    }

    fun examStats(): ExamSessionStats = dao.open().use { connection ->
        val query = "SELECT " +
                " SUM(participation) AS totalParticipation, " +
                " SUM(community) AS totalCommunity, " +
                " COUNT(*) AS candidates, " +
                " (SUM(participation) + SUM(community) + SUM(patience) + SUM(victory) + SUM(refinement) + SUM(performance) + SUM(achievement) + SUM(phantom)) AS promoTotal " +
                " FROM exam WHERE participation > 0"
        log(INFO, "examStats [$query]")
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .executeAndFetchFirst(ExamSessionStats::class.java)
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

    fun savePromotionScore(promoName: String, totalScore: Int): Connection = dao.open().use { connection ->
        val query = "INSERT INTO exam_awards(promo, score) VALUES (:promoName, :totalScore) "
        log(INFO, "savePromotionScore [$query] $promoName $totalScore")
        connection
            .createQuery(query)
            .addParameter("promoName", promoName)
            .addParameter("totalScore", totalScore)
            .executeUpdate()
    }

    fun examAward(): ExamAward = dao.open().use { connection ->
        val query = "SELECT * FROM exam_awards ORDER BY score DESC LIMIT 1"
        log(INFO, "examAward [$query]")
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .executeAndFetchFirst(ExamAward::class.java)
    }

    fun promoteHunter(examPlayer: ExamPlayer): Connection = dao.open().use { connection ->
        val query = "UPDATE exam SET hunter = 1 WHERE ${UserAccount.DISCORD.databaseId} = :discordId"
        log(INFO, "promoteExamPlayer [$query] ${examPlayer.discordId}")
        connection
            .createQuery(query)
            .addParameter("discordId", examPlayer.discordId)
            .executeUpdate()
    }

    fun resetSpec(specialization: ExamSpecialization): Connection = dao.open().use { connection ->
        val query = "UPDATE exam SET ${specialization.databaseId} = 0"
        log(INFO, "resetSpec [$query]")
        connection
            .createQuery(query)
            .executeUpdate()
    }

    fun incrementSpec(hunter: ExamPlayer, specialization: ExamSpecialization): Connection =
        dao.open().use { connection ->
            val updateQuery =
                "UPDATE exam SET ${specialization.databaseId} = ${specialization.databaseId} + 1 WHERE ${UserAccount.DISCORD.databaseId} = :discordId"
            val capQuery =
                "UPDATE exam SET ${specialization.databaseId} = 3 WHERE ${UserAccount.DISCORD.databaseId} = :discordId AND ${specialization.databaseId} > 3"

            log(INFO, "incrementSpec [$updateQuery] ${hunter.discordId}")
            connection.createQuery(updateQuery).addParameter("discordId", hunter.discordId).executeUpdate()

            log(INFO, "capQuery [$capQuery] ${hunter.discordId}")
            connection.createQuery(capQuery).addParameter("discordId", hunter.discordId).executeUpdate()
        }

    fun clearExamPoints(): Connection = dao.open().use { connection ->
        val query = "UPDATE exam SET " +
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

    fun clearPhantomPoints(): Connection = dao.open().use { connection ->
        val query = "UPDATE exam SET phantom = 0 "
        log(INFO, "clearPhantomPoints [$query]")
        connection.createQuery(query).executeUpdate()
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

    fun apiLadderPlayers(): List<ApiLadderPlayer> = dao.open().use { connection ->
        val query = "SELECT * FROM api_ladder_players "
        log(INFO, "apiLadderPlayers [$query]")
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .executeAndFetch(ApiLadderPlayer::class.java)
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

    fun createLadderPlayer(discordId: String, rating: Glickotlin.Player): Connection = dao.open().use { connection ->
        val query = "INSERT INTO ladder( " +
                " ${UserAccount.DISCORD.databaseId}, " +
                " rating, " +
                " deviation, " +
                " volatility) " +
                " VALUES (:discordId, :rating, :deviation, :volatility) "

        log(INFO, "createLadderPlayer [$query] $discordId")
        connection
            .createQuery(query)
            .addParameter("discordId", discordId)
            .addParameter("rating", rating.rating())
            .addParameter("deviation", rating.deviation())
            .addParameter("volatility", rating.volatility())
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
                " date, " +
                " rating, " +
                " deviation, " +
                " volatility) " +
                " VALUES (:discordId, :date, :rating, :deviation, :volatility) "

        log(INFO, "saveLadderRating [$query] $player")
        connection
            .createQuery(query)
            .addParameter("discordId", player.discordId)
            .addParameter("date", player.ratingDate)
            .addParameter("rating", player.rating)
            .addParameter("deviation", player.deviation)
            .addParameter("volatility", player.volatility)
            .executeUpdate()
    }

    fun ladderRatingsFor(discordId: String): List<LadderRating> = dao.open().use { connection ->
        val query = "SELECT * " +
                " FROM ladder_ratings " +
                " WHERE ${UserAccount.DISCORD.databaseId} = :discordId " +
                " ORDER BY date "
        log(INFO, "ladderRatingsFor [$query] $discordId")
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .addParameter("discordId", discordId)
            .executeAndFetch(LadderRating::class.java)
    }

    fun ladderRatingAt(playerId: String, date: Date): LadderRating? = dao.open().use { connection ->
        val query = "SELECT * " +
                " FROM ladder_ratings " +
                " WHERE ${UserAccount.DISCORD.databaseId} = :discordId  AND DATEDIFF(date, :date) < 0 " +
                " ORDER BY date DESC LIMIT 1 "

        log(INFO, "ladderRatingsFor [$query] $playerId $date")
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .addParameter("discordId", playerId)
            .addParameter("date", date)
            .executeAndFetchFirst(LadderRating::class.java)
    }

    fun cleanLadderRatings(): Connection = dao.open().use { connection ->
        val query = "DELETE FROM ladder_ratings WHERE DATEDIFF(NOW(), date) > 30"
        log(INFO, "cleanLadderHistory [$query]")
        connection.createQuery(query).executeUpdate()
    }

    // endregion

    // region league

    fun leaguePairings(rush: Int): List<LeaguePairing> = dao.open().use { connection ->
        val query = "SELECT * FROM league WHERE rush = :rush"
        log(INFO, "leaguePairings [$query]")
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .addParameter("rush", rush)
            .executeAndFetch(LeaguePairing::class.java)
    }

    fun currentRush(): Int = dao.open().use { connection ->
        val query = "SELECT MAX(rush) FROM league"
        log(INFO, "currentRush [$query]")
        connection
            .createQuery(query)
            .executeScalar(Int::class.java) ?: 1
    }

    fun updateLeaguePairings(pairing: LeaguePairing, gameId: Int, winnerId: String?): Connection =
        dao.open().use { connection ->
            val query = " UPDATE league SET " +
                    " game_id=:gameId, winner_id=:winnerId " +
                    " WHERE id = :id "
            log(INFO, "cleanLadderHistory [$query]")
            connection
                .createQuery(query)
                .addParameter("gameId", gameId)
                .addParameter("winnerId", winnerId)
                .addParameter("id", pairing.id)
                .executeUpdate()
        }

    fun savePairing(pairing: LeaguePairing): Connection = dao.open().use { connection ->
        val query = "INSERT INTO league(rush, date, first_player_id, second_player_id, game_id, winner_id, exempt) " +
                " VALUES (:rush, :date, :firstPlayerId, :secondPlayerId, :gameId, :winnerId, :exempt) "
        log(INFO, "savePairing [$query] $pairing")
        connection
            .createQuery(query)
            .addParameter("rush", pairing.rush)
            .addParameter("date", pairing.date)
            .addParameter("firstPlayerId", pairing.firstPlayerId)
            .addParameter("secondPlayerId", pairing.secondPlayerId)
            .addParameter("gameId", pairing.gameId)
            .addParameter("winnerId", pairing.winnerId)
            .addParameter("exempt", pairing.exempt)
            .executeUpdate()
    }

    // endregion
}
