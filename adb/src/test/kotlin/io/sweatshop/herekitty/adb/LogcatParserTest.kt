package io.sweatshop.herekitty.adb

import io.sweatshop.herekitty.domain.features.logs.model.LogLevel
import io.sweatshop.herekitty.domain.features.logs.model.LogLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class LogcatParserTest {

    private fun parse(vararg lines: String): List<LogLine> {
        var seq = 0L
        val parser = LogcatParser { seq++ }
        return buildList {
            lines.forEach { line -> parser.accept(line)?.let(::add) }
            parser.flush()?.let(::add)
        }
    }

    @Test
    fun `parses an epoch header as emitted by the device`() {
        val parsed = parse(
            "[          1787172644.106  2099: 2099 I/sensors-hal ]",
            "send_request:330, send_request start",
            "",
        )

        assertEquals(1, parsed.size)
        val line = parsed.single()
        assertEquals(1787172644106L, line.timestampMillis)
        assertEquals(2099, line.pid)
        assertEquals(2099, line.tid)
        assertEquals(LogLevel.INFO, line.level)
        assertEquals("sensors-hal", line.tag)
        assertEquals("send_request:330, send_request start", line.message)
    }

    /** A real tags: a leading slash and padding are part of the tag itself. */
    @Test
    fun `keeps punctuation and inner padding that belong to the tag`() {
        val parsed = parse("[          1787172772.720 30636:30636 D//      ExampleTagImpl ]", "hello", "")

        assertEquals("/      ExampleTagImpl", parsed.single().tag)
        assertEquals(LogLevel.DEBUG, parsed.single().level)
    }

    @Test
    fun `joins a multi line message and stops at the blank line`() {
        val parsed = parse(
            "[          1787172644.106  1: 2 W/multi ]",
            "first",
            "second",
            "third",
            "",
            "[          1787172644.200  1: 2 E/next ]",
            "after",
            "",
        )

        assertEquals(listOf("first\nsecond\nthird", "after"), parsed.map { it.message })
        assertEquals(listOf("multi", "next"), parsed.map { it.tag })
    }

    @Test
    fun `emits the pending record when a new header arrives without a blank line`() {
        val parsed = parse(
            "[          1787172644.106  1: 2 D/a ]",
            "one",
            "[          1787172644.107  1: 2 D/b ]",
            "two",
            "",
        )

        assertEquals(listOf("one", "two"), parsed.map { it.message })
    }

    @Test
    fun `turns logcat buffer separators into their own record`() {
        val parsed = parse("--------- beginning of main", "")

        assertEquals("beginning of main", parsed.single().message)
        assertEquals("logcat", parsed.single().tag)
    }

    @Test
    fun `parses the month day header used when epoch is unavailable`() {
        val parsed = parse("[ 08-19 12:04:01.123  1234: 5678 D/DateForm ]", "body", "")

        val line = parsed.single()
        assertEquals("DateForm", line.tag)
        assertEquals(1234, line.pid)
        assertEquals(5678, line.tid)
        assertEquals("body", line.message)
        assertTrue(line.timestampMillis > 0L)
    }

    @Test
    fun `assigns sequence numbers in arrival order`() {
        val parsed = parse(
            "[          1787172644.106  1: 2 D/a ]", "one", "",
            "[          1787172644.107  1: 2 D/b ]", "two", "",
        )

        assertEquals(listOf(0L, 1L), parsed.map { it.seq })
    }

    @Test
    fun `reuses one string for a repeated tag so noisy logs do not allocate per line`() {
        val parsed = parse(
            "[          1787172644.106  1: 2 D/repeated ]", "one", "",
            "[          1787172644.107  1: 2 D/repeated ]", "two", "",
        )

        assertSame(parsed[0].tag, parsed[1].tag)
    }

    @Test
    fun `ignores a line that only looks like a header`() {
        val parsed = parse("[ not really a header ]", "")

        assertEquals("[ not really a header ]", parsed.single().message)
        assertEquals("logcat", parsed.single().tag)
    }

    @Test
    fun `holds a record open until it is terminated`() {
        var seq = 0L
        val parser = LogcatParser { seq++ }

        assertNull(parser.accept("[          1787172644.106  1: 2 D/a ]"))
        assertNull(parser.accept("body"))
        assertEquals("body", parser.flush()?.message)
    }
}
