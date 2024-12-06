package com.fulgurogo.features.ssh

import com.fulgurogo.common.config.Config
import com.fulgurogo.common.logger.Logger.Level.ERROR
import com.fulgurogo.common.logger.Logger.Level.INFO
import com.fulgurogo.common.logger.log
import com.jcraft.jsch.JSch
import java.util.*

object SSHConnector {
    fun connect() = try {
        log(INFO, "connect")

        val localPort = Config.get("ssh.forwarded.port").toInt() // any free port can be used
        val config = Properties().apply {
            this["StrictHostKeyChecking"] = "no"
            this["ConnectionAttempts"] = "3"
        }
        with(JSch()) {
            addIdentity(Config.get("ssh.private.key.file"))
            val session = getSession(Config.get("ssh.user"), Config.get("ssh.host"), Config.get("ssh.port").toInt())
            session.setConfig(config)
            session.connect()
            log(INFO, "SSH session connected")

            val forwarded = session.setPortForwardingL(localPort, Config.get("db.host"), Config.get("db.port").toInt())
            log(INFO, "Forwarded port localhost:$forwarded -> ${Config.get("db.host")}:${Config.get("db.port")}")
        }

    } catch (e: Exception) {
        log(ERROR, e.message!!, e)
    }
}
