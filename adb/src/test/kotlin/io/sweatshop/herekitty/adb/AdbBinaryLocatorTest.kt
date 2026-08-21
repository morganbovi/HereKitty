package io.sweatshop.herekitty.adb

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AdbBinaryLocatorTest {

    private val candidates = AdbBinaryLocator().candidates()

    /** An adb the machine already has must win, so the copy we install is the last thing tried. */
    @Test
    fun `the managed install is searched last`() {
        assertEquals(managedAdb, candidates.last())
    }

    @Test
    fun `the explicit override is searched first`() {
        // Only meaningful when the variable is actually set, which is how the override is opted into.
        val override = System.getenv("HEREKITTY_ADB") ?: return

        assertEquals(override, candidates.first().path)
    }

    @Test
    fun `the usual system locations are searched before ours`() {
        val managedIndex = candidates.indexOf(managedAdb)
        val systemPaths = candidates.filterIndexed { index, _ -> index < managedIndex }

        assertTrue(
            systemPaths.any { it.path.contains("platform-tools") && it != managedAdb },
            "no SDK location is searched ahead of the managed copy",
        )
    }
}
