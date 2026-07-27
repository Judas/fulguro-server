package com.fulgurogo.igs

import org.apache.commons.net.telnet.TelnetClient
import java.io.IOException
import java.io.InputStream
import java.io.PrintStream

class IgsTelnetClient {
    companion object {
        /** IGS replies are small. Past this we are never going to find the pattern we are waiting for. */
        private const val MAX_RESPONSE_CHARS = 1 shl 20
    }

    private val telnetClient = TelnetClient()
    private var input: InputStream? = null
    private var output: PrintStream? = null

    @Throws(IOException::class)
    fun connect(server: String, port: Int, timeoutMs: Int) {
        telnetClient.connectTimeout = timeoutMs
        telnetClient.connect(server, port)
        // The read timeout matters as much as the connect one: without it a server that goes quiet mid-exchange parks
        // this thread forever, and since the caller is a service tick, that service never runs again.
        telnetClient.soTimeout = timeoutMs
        input = telnetClient.inputStream
        output = PrintStream(telnetClient.outputStream)
    }

    /**
     * Reads until [pattern] has been seen, returning everything read including it.
     *
     * @throws IOException if the connection ends first, if a read times out, or if the reply passes
     *   [MAX_RESPONSE_CHARS] without matching. End of stream used to be silent: `read()` returns -1, `(-1).toChar()`
     *   is U+FFFF, and appending that matches nothing — so a closed connection became a tight allocating loop that
     *   grew the buffer until the whole process died of OutOfMemoryError.
     */
    @Throws(IOException::class)
    fun readUntil(pattern: String): String {
        val stream = input ?: throw IOException("readUntil called before connect")
        val lastChar = pattern[pattern.length - 1]
        val response = StringBuilder()

        while (true) {
            val read = stream.read()
            if (read == -1) throw IOException("IGS closed the connection while waiting for \"$pattern\"")

            val char = read.toChar()
            response.append(char)
            if (char == lastChar && response.endsWith(pattern)) return response.toString()
            if (response.length > MAX_RESPONSE_CHARS)
                throw IOException("IGS sent over $MAX_RESPONSE_CHARS chars without \"$pattern\"")
        }
    }

    @Throws(IOException::class)
    fun write(value: String) {
        val stream = output ?: throw IOException("write called before connect")
        stream.println(value)
        stream.flush()
    }

    /** Safe on a client that never connected, and safe to call from a finally block. */
    fun disconnect() {
        input = null
        output = null
        try {
            telnetClient.disconnect()
        } catch (_: IOException) {
            // Nothing useful to do while cleaning up, and throwing here would mask the real failure.
        }
    }
}
