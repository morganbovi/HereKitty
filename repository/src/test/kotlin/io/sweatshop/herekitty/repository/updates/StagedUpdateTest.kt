package io.sweatshop.herekitty.repository.updates

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Finding the bundle to replace. Getting this wrong would point the swap at the wrong directory, so
 * it only accepts the exact shape a macOS app launcher lives in.
 */
class StagedUpdateTest {

    @Test
    fun `a launcher inside a bundle resolves to that bundle`() {
        assertEquals(
            "/Applications/HereKitty.app",
            StagedUpdate.bundleFrom("/Applications/HereKitty.app/Contents/MacOS/HereKitty").toString(),
        )
    }

    @Test
    fun `the bundle need not be in Applications`() {
        assertEquals(
            "/Users/someone/Desktop/HereKitty.app",
            StagedUpdate.bundleFrom("/Users/someone/Desktop/HereKitty.app/Contents/MacOS/HereKitty").toString(),
        )
    }

    /** Under `gradlew run` the launcher is a JVM somewhere else entirely; there is nothing to replace. */
    @Test
    fun `a plain java launcher is not inside a bundle`() {
        assertNull(StagedUpdate.bundleFrom("/Users/someone/.gradle/jdks/jbr/Contents/Home/bin/java"))
        assertNull(StagedUpdate.bundleFrom("/usr/bin/java"))
    }

    /** Only the real layout counts: a directory merely called MacOS is not a bundle. */
    @Test
    fun `a path that only looks similar is refused`() {
        assertNull(StagedUpdate.bundleFrom("/tmp/HereKitty.app/MacOS/HereKitty"))
        assertNull(StagedUpdate.bundleFrom("/tmp/Contents/MacOS/HereKitty"))
        assertNull(StagedUpdate.bundleFrom("/tmp/NotAnApp/Contents/MacOS/HereKitty"))
        assertNull(StagedUpdate.bundleFrom("HereKitty"))
    }
}
