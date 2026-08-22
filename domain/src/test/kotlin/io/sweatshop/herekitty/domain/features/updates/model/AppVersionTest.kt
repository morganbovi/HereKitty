package io.sweatshop.herekitty.domain.features.updates.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppVersionTest {

    private fun version(raw: String) = AppVersion.parse(raw) ?: error("$raw did not parse")

    /** The whole reason not to compare tags as strings. */
    @Test
    fun `ordering is numeric, not alphabetical`() {
        assertTrue(version("1.10.0") > version("1.9.0"))
        assertTrue(version("2.0.0") > version("1.99.99"))
        assertTrue(version("1.0.10") > version("1.0.9"))
    }

    @Test
    fun `a release tag's v prefix is accepted`() {
        assertEquals(AppVersion(1, 2, 3), version("v1.2.3"))
        assertEquals(AppVersion(1, 2, 3), version("1.2.3"))
        assertEquals(AppVersion(1, 2, 3), version("  v1.2.3  "))
    }

    @Test
    fun `missing components default to zero`() {
        assertEquals(AppVersion(1, 0, 0), version("1"))
        assertEquals(AppVersion(1, 2, 0), version("1.2"))
    }

    /** Running a candidate must still be offered the release it precedes. */
    @Test
    fun `a pre-release is older than its release`() {
        assertTrue(version("1.0.0") > version("1.0.0-rc1"))
        assertTrue(version("1.0.0-rc1") < version("1.0.0"))
        assertTrue(version("1.0.1-rc1") > version("1.0.0"))
    }

    @Test
    fun `pre-releases of the same version order against each other`() {
        assertTrue(version("1.0.0-rc2") > version("1.0.0-rc1"))
        assertEquals(0, version("1.0.0-rc1").compareTo(version("1.0.0-rc1")))
    }

    @Test
    fun `equal versions compare equal`() {
        assertEquals(0, version("1.2.3").compareTo(version("v1.2.3")))
    }

    /**
     * Null rather than a zeroed version: something unparseable must not read as older than everything
     * and announce a phantom update.
     */
    @Test
    fun `nonsense does not parse`() {
        assertNull(AppVersion.parse(""))
        assertNull(AppVersion.parse("   "))
        assertNull(AppVersion.parse("latest"))
        assertNull(AppVersion.parse("1.x.3"))
        assertNull(AppVersion.parse("1.2.3.4"))
        assertNull(AppVersion.parse("-1.2.3"))
        assertNull(AppVersion.parse("v"))
    }

    @Test
    fun `toString round-trips through parse`() {
        listOf("1.2.3", "0.0.1", "10.20.30", "1.0.0-rc1").forEach { raw ->
            assertEquals(raw, version(raw).toString())
        }
    }

    @Test
    fun `the running version is the one the build generated`() {
        assertEquals(AppVersion.parse(io.sweatshop.herekitty.domain.BuildInfo.VERSION), AppVersion.Current)
    }
}
