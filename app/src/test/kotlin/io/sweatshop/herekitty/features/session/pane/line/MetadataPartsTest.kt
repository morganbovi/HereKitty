package io.sweatshop.herekitty.features.session.pane.line

import io.sweatshop.herekitty.domain.features.logs.model.LogLevel
import io.sweatshop.herekitty.domain.features.logs.model.LogLine
import io.sweatshop.herekitty.domain.features.settings.model.LogColumns
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MetadataPartsTest {

    private val line = LogLine(
        seq = 1L,
        timestampMillis = 1_787_172_644_106L,
        pid = 2099,
        tid = 3244,
        level = LogLevel.WARN,
        tag = "/      ExampleTagImpl",
        message = "hello",
    )

    private val allOn = LogColumns(timestamp = true, level = true, tag = true, processIds = true)

    /** The tag is the only part that is also a control, so exactly one part may claim it. */
    @Test
    fun `only the tag is marked as the double-click target`() {
        val parts = stackedMetadataParts(line, allOn)

        assertEquals(1, parts.count { it.isTag })
        assertTrue(parts.first().isTag, "the tag should lead the line")
    }

    @Test
    fun `the tag is trimmed for display but stays the tag`() {
        assertEquals("ExampleTagImpl", stackedMetadataParts(line, allOn).first().text.removePrefix("/").trim())
    }

    @Test
    fun `parts follow the enabled columns in order`() {
        val parts = stackedMetadataParts(line, allOn).map { it.text }

        assertEquals(4, parts.size)
        assertEquals("W", parts[2])
        assertEquals("2099-3244", parts[3])
    }

    @Test
    fun `switching a column off drops its part`() {
        val parts = stackedMetadataParts(
            line,
            LogColumns(timestamp = false, level = false, tag = true, processIds = false),
        )

        assertEquals(1, parts.size)
        assertTrue(parts.single().isTag)
    }

    @Test
    fun `with the tag hidden there is nothing to double-click`() {
        val parts = stackedMetadataParts(
            line,
            LogColumns(timestamp = true, level = false, tag = false, processIds = false),
        )

        assertTrue(parts.none { it.isTag })
        // Not asserting the rendered time: it is formatted in the machine's zone.
        assertEquals(1, parts.size)
    }

    @Test
    fun `everything off produces no metadata line at all`() {
        val parts = stackedMetadataParts(
            line,
            LogColumns(timestamp = false, level = false, tag = false, processIds = false),
        )

        assertTrue(parts.isEmpty())
    }
}
