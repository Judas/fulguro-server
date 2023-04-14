package com.fulgurogo

object Config {
    const val DEV = false

    object SSH {
        const val USER = "useless-only-needed-in-dev"
        const val HOST = "useless-only-needed-in-dev"
        const val PORT = -1 // useless-only-needed-in-dev
        const val PRIVATE_KEY_FILE = "useless-only-needed-in-dev"
        const val FORWARDED_PORT = -1 // useless-only-needed-in-dev
    }

    object Database {
        const val HOST = "localhost"
        const val PORT = 3306
        const val NAME = "<ENTER_MYSQL_DB_NAME>"
        const val USER = "<ENTER_MYSQL_USERNAME>"
        const val PASSWORD = "<ENTER_MYSQL_PASSWORD>"
    }

    object Bot {
        const val TOKEN = "<ENTER_BOT_TOKEN>"
        const val EMBED_COLOR = 0xFCA311
        const val GUILD_ID = "<ENTER_GUILD_ID>"
    }

    object Server {
        const val PORT = 4567
    }

    object Exam {
        const val CHANNEL_ID = "<ENTER_EXAM_CHANNEL_ID>"
        const val HOF_CHANNEL_ID = "<ENTER_EXAM_HOF_CHANNEL_ID>"
    }

    object Ladder {
        const val INITIAL_RATING = 1150.0
        const val INITIAL_DEVIATION = 350.0
        const val INITIAL_VOLATILITY = 0.06
        const val PLAY_CHANNEL = "<ENTER_PLAY_CHANNEL_ID>"
        const val WEBSITE_URL = "https://fulguro-gold.onrender.com"
    }

    object Kgs {
        const val API_URL = "http://localhost:8080/kgs-proxy/access"
        const val USERNAME = "<ENTER_KGS_USERNAME>"
        const val PASSWORD = "<ENTER_KGS_PASSWORD>"
        const val API_DELAY_IN_SECONDS = 1
        const val ROOM_CHANNEL_ID = -1 // <ENTER_KGS_ROOM_CHANNEL_ID>
        const val ARCHIVES_URL = "https://www.gokgs.com/gameArchives.jsp"
        const val GAME_LINK = "http://files.gokgs.com/games"
        const val GRAPH_URL = "https://www.gokgs.com/graphPage.jsp"
    }

    object Ogs {
        const val API_URL = "https://online-go.com/api/v1"
        const val TERMINATION_API_URL = "https://online-go.com/termination-api"
        const val WEBSITE_URL = "https://online-go.com"
    }

    object Fox {
        const val API_URL = "http://happyapp.huanle.qq.com/cgi-bin/CommonMobileCGI"
        const val USER_INFO = "TXWQFetchPersonalInfo?username="
        const val USER_GAMES = "TXWQFetchChessList?type=3&username="
        const val GAME_SGF = "TXWQFetchChess?chessid="
        const val GAME_LINK = "http://h5.foxwq.com/txwqshare/index.html?chessid="
    }

    object Igs {
        const val SERVER_ADDRESS = "igs.joyjoy.net"
        const val SERVER_PORT = 6969
        const val USERNAME = "<ENTER_IGS_USERNAME>"
        const val PASSWORD = "<ENTER_IGS_PASSWORD>"
    }

    object Ffg {
        const val WEBSITE_URL = "https://ffg.jeudego.org"
    }

    object Egf {
        const val WEBSITE_URL = "https://www.europeangodatabase.eu/EGD/Player_Card.php"
    }
}
