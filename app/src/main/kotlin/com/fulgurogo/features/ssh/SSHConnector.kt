package com.fulgurogo.features.ssh

import com.fulgurogo.Config
import com.fulgurogo.utilities.Logger.Level.ERROR
import com.fulgurogo.utilities.Logger.Level.INFO
import com.fulgurogo.utilities.log
import com.jcraft.jsch.JSch
import java.io.File
import java.util.*

object SSHConnector {
    fun connect() = try {
        log(INFO, "connect")

        val localPort = Config.SSH.FORWARDED_PORT // any free port can be used
        val config = Properties().apply {
            this["StrictHostKeyChecking"] = "no"
            this["ConnectionAttempts"] = "3"
        }
        with(JSch()) {
            addIdentity(Config.SSH.PRIVATE_KEY_FILE)
            val session = getSession(Config.SSH.USER, Config.SSH.HOST, Config.SSH.PORT)
            session.setConfig(config)
            session.connect()
            log(INFO, "SSH session connected")

            val forwarded = session.setPortForwardingL(localPort, Config.Database.HOST, Config.Database.PORT)
            log(INFO, "Forwarded port localhost:$forwarded -> ${Config.Database.HOST}:${Config.Database.PORT}")
        }

    } catch (e: Exception) {
        log(ERROR, e.message!!, e)
    }
}
