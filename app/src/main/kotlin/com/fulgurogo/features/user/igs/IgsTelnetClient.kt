package com.fulgurogo.features.user.igs

import com.fulgurogo.common.logger.Logger.Level.INFO
import com.fulgurogo.common.logger.log
import org.apache.commons.net.telnet.TelnetClient
import java.io.IOException
import java.io.InputStream
import java.io.PrintStream

class IgsTelnetClient {
    private val telnetClient = TelnetClient()
    private var input: InputStream? = null
    private var output: PrintStream? = null

    @Throws(IOException::class)
    fun connect(server: String, port: Int) {
        log(INFO, "connect $server:$port")
        telnetClient.connect(server, port)
        input = telnetClient.inputStream
        output = PrintStream(telnetClient.outputStream)
    }

    @Throws(IOException::class)
    fun readUntil(pattern: String): String {
        log(INFO, "readUntil $pattern")
        val lastChar = pattern[pattern.length - 1]
        val sb = StringBuilder()
        var ch = input!!.read().toChar()
        while (true) {
            sb.append(ch)
            if (ch == lastChar) {
                if (sb.toString().endsWith(pattern)) {
                    return sb.toString()
                }
            }
            ch = input!!.read().toChar()
        }
    }

    fun write(value: String) {
        log(INFO, "write $value")
        output!!.println(value)
        output!!.flush()
    }

    @Throws(IOException::class)
    fun disconnect() {
        log(INFO, "disconnect")
        telnetClient.disconnect()
    }
}
