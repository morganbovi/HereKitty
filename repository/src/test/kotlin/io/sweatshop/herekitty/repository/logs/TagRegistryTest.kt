package io.sweatshop.herekitty.repository.logs

import io.sweatshop.herekitty.domain.features.logs.model.LogLevel
import io.sweatshop.herekitty.domain.features.logs.model.LogLine
import kotlin.test.Test
import kotlin.test.assertEquals

class TagRegistryTest {

    private fun line(tag: String, level: LogLevel = LogLevel.DEBUG, timestampMillis: Long = 1L) =
        LogLine(1L, timestampMillis, 1, 2, level, tag, "message")

    @Test
    fun `lists tags alphabetically regardless of how much traffic each carries`() {
        val registry = TagRegistry()
        repeat(500) { registry.record(line("zebra")) }
        registry.record(line("Alpha"))
        repeat(50) { registry.record(line("middle")) }

        assertEquals(listOf("Alpha", "middle", "zebra"), registry.snapshot().map { it.tag })
    }

    /** Real tags carry padding and punctuation; ordering on it would hide them from the eye. */
    @Test
    fun `orders on the first letter, not on padding or punctuation`() {
        val registry = TagRegistry()
        listOf("/      ExampleTagImpl", "Codec", "  Bravo  ", "zebra").forEach { registry.record(line(it)) }

        assertEquals(
            listOf("  Bravo  ", "Codec", "/      ExampleTagImpl", "zebra"),
            registry.snapshot().map { it.tag },
        )
    }

    @Test
    fun `keeps a tag made only of punctuation in the list`() {
        val registry = TagRegistry()
        listOf("---", "Radio").forEach { registry.record(line(it)) }

        assertEquals(setOf("---", "Radio"), registry.snapshot().map { it.tag }.toSet())
    }

    @Test
    fun `counts every line and keeps the tag verbatim`() {
        val registry = TagRegistry()
        repeat(3) { registry.record(line("/      ExampleTagImpl")) }

        val stats = registry.snapshot().single()
        assertEquals("/      ExampleTagImpl", stats.tag)
        assertEquals(3L, stats.count)
    }

    @Test
    fun `remembers the loudest level and the most recent sighting`() {
        val registry = TagRegistry()
        registry.record(line("Radio", LogLevel.DEBUG, timestampMillis = 10L))
        registry.record(line("Radio", LogLevel.ERROR, timestampMillis = 20L))
        registry.record(line("Radio", LogLevel.INFO, timestampMillis = 30L))

        val stats = registry.snapshot().single()
        assertEquals(LogLevel.ERROR, stats.highestLevel)
        assertEquals(30L, stats.lastSeenMillis)
    }

    @Test
    fun `clear forgets every tag`() {
        val registry = TagRegistry()
        registry.record(line("Radio"))

        registry.clear()

        assertEquals(emptyList(), registry.snapshot())
    }
}
