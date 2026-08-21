package io.sweatshop.herekitty.repository.logs

import io.sweatshop.herekitty.domain.features.logs.model.LogLevel
import io.sweatshop.herekitty.domain.features.logs.model.LogLine
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DuplicateLinesTest {

    private fun line(
        seq: Long = 1L,
        timestampMillis: Long = 1_000L,
        pid: Int = 2099,
        level: LogLevel = LogLevel.DEBUG,
        tag: String = "sensors-hal",
        message: String = "send_sync_sensor_request:531, waiting",
    ) = LogLine(seq, timestampMillis, pid, 3244, level, tag, message)

    /** The whole point: the same spam line at a different moment is still the same line. */
    @Test
    fun `the timestamp is ignored`() {
        assertTrue(isRepeatOf(line(timestampMillis = 1_000L), line(timestampMillis = 9_999L)))
    }

    @Test
    fun `a different message is not a repeat`() {
        assertFalse(isRepeatOf(line(message = "one"), line(message = "two")))
    }

    @Test
    fun `a different tag is not a repeat`() {
        assertFalse(isRepeatOf(line(tag = "sensors-hal"), line(tag = "SLocation")))
    }

    @Test
    fun `a different level is not a repeat`() {
        assertFalse(isRepeatOf(line(level = LogLevel.DEBUG), line(level = LogLevel.ERROR)))
    }

    /** The same words from two processes are two different events. */
    @Test
    fun `a different process is not a repeat`() {
        assertFalse(isRepeatOf(line(pid = 2099), line(pid = 30636)))
    }

    @Test
    fun `nothing to compare against is not a repeat`() {
        assertFalse(isRepeatOf(previous = null, current = line()))
    }

    @Test
    fun `an empty message can still repeat`() {
        assertTrue(isRepeatOf(line(message = ""), line(message = "")))
    }
}
