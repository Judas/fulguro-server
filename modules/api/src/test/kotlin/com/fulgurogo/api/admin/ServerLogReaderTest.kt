package com.fulgurogo.api.admin

import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ServerLogReaderTest {
    @Test
    fun `empty file returns no lines`() {
        val path = Files.createTempFile("gold-empty-log", ".log")
        try {
            assertEquals(emptyList(), ServerLogReader(path).tail())
        } finally {
            Files.deleteIfExists(path)
        }
    }

    @Test
    fun `short file is returned in chronological order`() {
        withLog("first\nsecond\n") { reader ->
            assertEquals(listOf("first", "second"), reader.tail())
        }
    }

    @Test
    fun `only the last five hundred complete lines are returned`() {
        val content = (1..600).joinToString(separator = "\n", postfix = "\n") { "line-$it" }
        withLog(content) { reader ->
            val lines = reader.tail()!!
            assertEquals(500, lines.size)
            assertEquals("line-101", lines.first())
            assertEquals("line-600", lines.last())
        }
    }

    @Test
    fun `read window is bounded and starts on a complete line`() {
        val content = (1..12_000).joinToString(separator = "\n", postfix = "\n") {
            "line-$it-${"x".repeat(100)}"
        }
        withLog(content) { reader ->
            val lines = reader.tail()!!
            assertEquals(500, lines.size)
            assertTrue(lines.first().startsWith("line-11501-"))
            assertEquals("line-12000-${"x".repeat(100)}", lines.last())
        }
    }

    @Test
    fun `unfinished final line is omitted`() {
        withLog("complete\npartial") { reader ->
            assertEquals(listOf("complete"), reader.tail())
        }
    }

    @Test
    fun `missing file is unavailable`() {
        val path = Files.createTempDirectory("gold-missing-log").resolve("missing.log")
        try {
            assertEquals(null, ServerLogReader(path).tail())
        } finally {
            Files.deleteIfExists(path.parent)
        }
    }

    private fun withLog(content: String, assertion: (ServerLogReader) -> Unit) {
        val path = Files.createTempFile("gold-log", ".log")
        try {
            path.writeText(content)
            assertion(ServerLogReader(path))
        } finally {
            Files.deleteIfExists(path)
        }
    }
}
