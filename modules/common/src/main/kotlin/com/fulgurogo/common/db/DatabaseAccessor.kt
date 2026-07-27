package com.fulgurogo.common.db

import com.fulgurogo.common.config.Config
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.sql2o.Connection
import org.sql2o.Query
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
    })

    inline fun <T> withDao(block: (Connection) -> T): T = dao.open().use(block)
}

/**
 * Creates a query that maps `snake_case` columns onto `camelCase` properties.
 *
 * Always prefer this over [Connection.createQuery]: without the derivation a snake_case column silently maps to null,
 * because most reads also set `throwOnMappingFailure(false)`.
 */
fun Connection.query(sql: String): Query = createQuery(sql).setAutoDeriveColumnNames(true)
