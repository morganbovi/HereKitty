package io.sweatshop.herekitty.updates

import io.sweatshop.herekitty.domain.features.updates.model.InstallerKind
import io.sweatshop.herekitty.domain.features.updates.model.ReleaseAsset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlatformAssetTest {

    private fun asset(name: String) = ReleaseAsset(name, "https://example/$name", 1L)

    private val release = listOf(
        asset("HereKitty-1.1.0-macos-arm64.dmg"),
        asset("HereKitty-1.1.0-macos-arm64.zip"),
        asset("HereKitty-1.1.0-macos-x64.dmg"),
        asset("HereKitty-1.1.0-windows-x64.msi"),
        asset("HereKitty-1.1.0-linux-x64.deb"),
        asset("SHA256SUMS"),
    )

    @Test
    fun `each platform gets its own build`() {
        assertEquals(
            "HereKitty-1.1.0-macos-arm64.dmg",
            PlatformAsset.select(release, InstallerKind.Dmg, "arm64")?.name,
        )
        assertEquals(
            "HereKitty-1.1.0-macos-x64.dmg",
            PlatformAsset.select(release, InstallerKind.Dmg, "x64")?.name,
        )
        assertEquals(
            "HereKitty-1.1.0-windows-x64.msi",
            PlatformAsset.select(release, InstallerKind.Msi, "x64")?.name,
        )
        assertEquals(
            "HereKitty-1.1.0-linux-x64.deb",
            PlatformAsset.select(release, InstallerKind.Deb, "x64")?.name,
        )
    }

    /** Downloading 88 MB of the wrong architecture only to fail to launch is worse than not offering. */
    @Test
    fun `an architecture that was not published is refused, not substituted`() {
        val armOnly = listOf(asset("HereKitty-1.1.0-macos-arm64.dmg"))

        assertNull(PlatformAsset.select(armOnly, InstallerKind.Dmg, "x64"))
    }

    @Test
    fun `an asset with no architecture in its name is taken as universal`() {
        val unlabelled = listOf(asset("HereKitty-1.1.0.dmg"))

        assertEquals("HereKitty-1.1.0.dmg", PlatformAsset.select(unlabelled, InstallerKind.Dmg, "arm64")?.name)
        assertEquals("HereKitty-1.1.0.dmg", PlatformAsset.select(unlabelled, InstallerKind.Dmg, "x64")?.name)
    }

    /** Two unlabelled candidates are ambiguous, and guessing would install the wrong one. */
    @Test
    fun `ambiguous unlabelled assets are refused`() {
        val two = listOf(asset("HereKitty.dmg"), asset("HereKitty-old.dmg"))

        assertNull(PlatformAsset.select(two, InstallerKind.Dmg, "arm64"))
    }

    @Test
    fun `the checksums file is never mistaken for an installer`() {
        assertNull(PlatformAsset.select(listOf(asset("SHA256SUMS")), InstallerKind.Dmg, "arm64"))
    }

    /**
     * Asserted without naming a platform on purpose: this suite runs on a Linux CI runner as well as
     * on a developer's Mac, and pinning either one would fail on the other.
     */
    @Test
    fun `this machine picks the asset matching its own installer kind`() {
        val picked = PlatformAsset.installerForThisMachine(release)

        assertNotNull(picked, "a release carrying every platform should serve this one")
        assertTrue(
            picked.name.endsWith(".${PlatformAsset.currentKind.extension}"),
            "picked ${picked.name} for ${PlatformAsset.currentKind}",
        )
        assertTrue(picked.name.contains(PlatformAsset.currentArchitecture), "picked ${picked.name}")
    }

    /** A person gets the disk image; the updater gets the bundle it can actually swap. */
    @Test
    fun `the updater prefers the archived bundle over the disk image`() {
        val installer = PlatformAsset.installerForThisMachine(release)
        val payload = PlatformAsset.updatePayloadForThisMachine(release)

        assertNotNull(installer)
        assertNotNull(payload)
        assertTrue(installer.name.endsWith(".${PlatformAsset.currentKind.extension}"), installer.name)
        assertTrue(payload.name.endsWith(".${PlatformAsset.currentUpdateKind.extension}"), payload.name)
    }

    /** A release published before zips were added must still be updatable, not silently skipped. */
    @Test
    fun `the updater falls back to the installer when no archive was published`() {
        val noZip = release.filterNot { it.name.endsWith(".zip") }

        assertEquals(
            PlatformAsset.installerForThisMachine(noZip)?.name,
            PlatformAsset.updatePayloadForThisMachine(noZip)?.name,
        )
    }
}
