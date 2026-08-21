package io.sweatshop.herekitty.features.session.pane.line

import io.sweatshop.herekitty.domain.features.logs.model.LogLevel
import io.sweatshop.herekitty.domain.features.logs.model.LogLine
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LineGroupingTest {

    private fun line(
        timestampMillis: Long,
        tag: String = "Radio",
        level: LogLevel = LogLevel.DEBUG,
        pid: Int = 2099,
    ) = LogLine(1L, timestampMillis, pid, 3244, level, tag, "message")

    @Test
    fun `the first line always gets a header`() {
        assertTrue(needsMetadataHeader(previous = null, current = line(1_000L)))
    }

    /** The point of grouping: a burst from one tag should not repeat its header on every line. */
    @Test
    fun `a run from the same tag at the same moment shares one header`() {
        assertFalse(needsMetadataHeader(line(1_000L), line(1_050L)))
        assertFalse(needsMetadataHeader(line(1_000L), line(1_999L)))
    }

    @Test
    fun `a different tag starts a new block`() {
        assertTrue(needsMetadataHeader(line(1_000L, tag = "Radio"), line(1_010L, tag = "Outcome")))
    }

    @Test
    fun `a different level starts a new block`() {
        assertTrue(
            needsMetadataHeader(
                line(1_000L, level = LogLevel.DEBUG),
                line(1_010L, level = LogLevel.ERROR),
            ),
        )
    }

    @Test
    fun `a different process starts a new block`() {
        assertTrue(needsMetadataHeader(line(1_000L, pid = 2099), line(1_010L, pid = 30636)))
    }

    @Test
    fun `a pause starts a new block even from the same tag`() {
        assertTrue(needsMetadataHeader(line(1_000L), line(2_000L)))
        assertTrue(needsMetadataHeader(line(1_000L), line(9_000L)))
    }

    /** Recorded lines can arrive slightly out of order; the gap should be judged either way. */
    @Test
    fun `a large backwards jump also starts a new block`() {
        assertTrue(needsMetadataHeader(line(9_000L), line(1_000L)))
    }
}
