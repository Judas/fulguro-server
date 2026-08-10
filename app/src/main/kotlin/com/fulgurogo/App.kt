package com.fulgurogo

import com.fulgurogo.api.ApiModule
import com.fulgurogo.clean.CleanModule
import com.fulgurogo.common.config.Config
import com.fulgurogo.common.db.ssh.SSHConnector
import com.fulgurogo.discord.DiscordModule
import com.fulgurogo.fgc.FgcModule
import com.fulgurogo.gold.GoldModule
import com.fulgurogo.house.HouseModule
import com.fulgurogo.kgs.KgsModule
import com.fulgurogo.league.LeagueModule
import com.fulgurogo.ogs.OgsModule
import com.fulgurogo.ping.PingModule

fun main() {
    val isDebug = Config.get("debug").toBoolean()

    // In dev we need to connect via SSH to the server for the MySQL access (only local connection allowed)
    if (isDebug) SSHConnector.connect()

    // Data aggregator modules
    DiscordModule.init()
    KgsModule.init()
    OgsModule.init()

    // Community modules
    GoldModule.init()
    FgcModule.init()
    HouseModule.init()
    LeagueModule.init()
    ApiModule.init(isDebug)

    // TODO CardsModule

    // Utility modules
    PingModule.init()
    CleanModule.init()
}
