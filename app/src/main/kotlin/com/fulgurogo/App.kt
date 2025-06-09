package com.fulgurogo

import com.fulgurogo.api.ApiModule
import com.fulgurogo.clean.CleanModule
import com.fulgurogo.common.config.Config
import com.fulgurogo.common.db.ssh.SSHConnector
import com.fulgurogo.discord.DiscordModule
import com.fulgurogo.egf.EgfModule
import com.fulgurogo.ffg.FfgModule
import com.fulgurogo.fgc.FgcModule
import com.fulgurogo.fox.FoxModule
import com.fulgurogo.gold.GoldModule
import com.fulgurogo.igs.IgsModule
import com.fulgurogo.kgs.KgsModule
import com.fulgurogo.ogs.OgsModule
import com.fulgurogo.ping.PingModule

const val TAG = "OldAppModule"

fun main() {
    val isDebug = Config.get("debug").toBoolean()

    // In dev we need to connect via SSH to the server for the MySQL access (only local connection allowed)
    if (isDebug) SSHConnector.connect()

    // Data aggregator modules
    DiscordModule.init()
    KgsModule.init()
    OgsModule.init()
    FoxModule.init()
    IgsModule.init()
    FfgModule.init()
    EgfModule.init()

    // Community modules
    GoldModule.init()
    FgcModule.init()
    ApiModule.init(isDebug)

    // TODO HouseModule
    // TODO CardsModule

    // Utility modules
    PingModule.init()
    CleanModule.init()
}
