package com.fulgurogo.common.db

import com.fulgurogo.common.config.Config
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.sql2o.Sql2o
import org.sql2o.quirks.NoQuirks
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

    fun dao(): Sql2o = Sql2o(dataSource, object : NoQuirks() {
        init {
            converters[Date::class.java] = CustomDateConverter()
        }
    })
}
