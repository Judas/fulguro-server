package com.fulgurogo.api.admin

import com.fulgurogo.common.logger.ServerLog
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

fun interface LogReader {
    fun tail(): List<String>?
}

class ServerLogReader(
    private val path: Path = ServerLog.path(),
    private val maxLines: Int = 500,
    private val maxBytes: Long = 1024L * 1024L,
) : LogReader {
    override fun tail(): List<String>? {
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) return null
        return RandomAccessFile(path.toFile(), "r").use { file ->
            val length = file.length()
            val start = (length - maxBytes).coerceAtLeast(0)
            val byteCount = (length - start).toInt()
            val bytes = ByteArray(byteCount)
            file.seek(start)
            file.readFully(bytes)

            if (bytes.isEmpty()) return emptyList()
            val endsWithNewline = bytes.last() == '\n'.code.toByte() || bytes.last() == '\r'.code.toByte()
            val lines = String(bytes, StandardCharsets.UTF_8)
                .split('\n')
                .map { it.removeSuffix("\r") }
                .toMutableList()

            if (start > 0 && lines.isNotEmpty()) lines.removeFirst()
            if (!endsWithNewline && lines.isNotEmpty()) lines.removeLast()
            while (lines.lastOrNull()?.isEmpty() == true) lines.removeLast()
            lines.takeLast(maxLines)
        }
    }
}
