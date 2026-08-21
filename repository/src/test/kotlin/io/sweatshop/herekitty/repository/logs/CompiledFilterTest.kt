package io.sweatshop.herekitty.repository.logs

import io.sweatshop.herekitty.domain.features.logs.model.LogFilter
import io.sweatshop.herekitty.domain.features.logs.model.LogLevel
import io.sweatshop.herekitty.domain.features.logs.model.LogLine
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompiledFilterTest {

    private fun line(tag: String = "Radio", message: String = "sent packet", level: LogLevel = LogLevel.DEBUG) =
        LogLine(1L, 1L, 1, 2, level, tag, message)

    private fun matches(filter: LogFilter, line: LogLine) = CompiledFilter(filter).matches(line)

    @Test
    fun `an empty filter passes everything`() {
        assertTrue(matches(LogFilter(), line()))
    }

    @Test
    fun `keeps only the selected tags`() {
        val filter = LogFilter(tags = setOf("Radio", "BT_TX"))

        assertTrue(matches(filter, line(tag = "Radio")))
        assertTrue(matches(filter, line(tag = "BT_TX")))
        assertFalse(matches(filter, line(tag = "sensors-hal")))
    }

    @Test
    fun `matches a tag exactly rather than by prefix`() {
        val filter = LogFilter(tags = setOf("Radio"))

        assertFalse(matches(filter, line(tag = "RadioLossless")))
    }

    @Test
    fun `matches the real padded tag verbatim`() {
        val filter = LogFilter(tags = setOf("/      ExampleTagImpl"))

        assertTrue(matches(filter, line(tag = "/      ExampleTagImpl")))
        assertFalse(matches(filter, line(tag = "ExampleTagImpl")))
    }

    @Test
    fun `excluded tags win over an empty include set`() {
        val filter = LogFilter(excludeTags = setOf("sensors-hal"))

        assertFalse(matches(filter, line(tag = "sensors-hal")))
        assertTrue(matches(filter, line(tag = "Radio")))
    }

    @Test
    fun `drops lines below the minimum level`() {
        val filter = LogFilter(minLevel = LogLevel.WARN)

        assertFalse(matches(filter, line(level = LogLevel.DEBUG)))
        assertTrue(matches(filter, line(level = LogLevel.WARN)))
        assertTrue(matches(filter, line(level = LogLevel.ERROR)))
    }

    @Test
    fun `searches the message and the tag, ignoring case by default`() {
        assertTrue(matches(LogFilter(query = "PACKET"), line(message = "sent packet")))
        assertTrue(matches(LogFilter(query = "radio"), line(tag = "Radio")))
        assertFalse(matches(LogFilter(query = "missing"), line()))
    }

    @Test
    fun `honours match case`() {
        val filter = LogFilter(query = "PACKET", matchCase = true)

        assertFalse(matches(filter, line(message = "sent packet")))
        assertTrue(matches(filter.copy(query = "packet"), line(message = "sent packet")))
    }

    @Test
    fun `applies a regular expression when asked`() {
        val filter = LogFilter(query = """seq=\d+""", useRegex = true)

        assertTrue(matches(filter, line(message = "tx seq=42 ok")))
        assertFalse(matches(filter, line(message = "tx seq=none")))
    }

    /** A pane should not blank out while a pattern is still being typed. */
    @Test
    fun `a half typed pattern passes everything through`() {
        val filter = LogFilter(query = "seq=(", useRegex = true)

        assertTrue(CompiledFilter(filter).hasInvalidRegex)
        assertTrue(matches(filter, line()))
    }

    @Test
    fun `combines tag, level and query`() {
        val filter = LogFilter(tags = setOf("Radio"), minLevel = LogLevel.INFO, query = "packet")

        assertTrue(matches(filter, line(tag = "Radio", message = "sent packet", level = LogLevel.WARN)))
        assertFalse(matches(filter, line(tag = "Radio", message = "sent packet", level = LogLevel.DEBUG)))
        assertFalse(matches(filter, line(tag = "Other", message = "sent packet", level = LogLevel.WARN)))
        assertFalse(matches(filter, line(tag = "Radio", message = "idle", level = LogLevel.WARN)))
    }
}
