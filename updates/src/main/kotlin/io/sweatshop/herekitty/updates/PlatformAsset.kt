package io.sweatshop.herekitty.updates

import io.sweatshop.herekitty.domain.features.updates.model.InstallerKind
import io.sweatshop.herekitty.domain.features.updates.model.ReleaseAsset

/**
 * Picks the asset this machine can actually install.
 *
 * Matched on extension *and* architecture, because the release carries one file per platform and they
 * are named alike apart from those two parts. An arm64 machine offered an x64 build would download
 * 88 MB and then fail to launch, so a miss returns null rather than the first plausible file.
 */
object PlatformAsset {

    /** What a person would download and install by hand. */
    fun installerForThisMachine(assets: List<ReleaseAsset>): ReleaseAsset? =
        select(assets, currentKind, currentArchitecture)

    /**
     * What the updater should fetch, which on macOS is not what a person downloads: replacing the app
     * in place needs the bundle, so the zip is preferred and the disk image is the fallback.
     */
    fun updatePayloadForThisMachine(assets: List<ReleaseAsset>): ReleaseAsset? =
        select(assets, currentUpdateKind, currentArchitecture)
            ?: select(assets, currentKind, currentArchitecture)

    internal fun select(
        assets: List<ReleaseAsset>,
        kind: InstallerKind,
        architecture: String,
    ): ReleaseAsset? {
        val matchingKind = assets.filter { it.name.endsWith(".${kind.extension}", ignoreCase = true) }

        // An architecture in the name has to agree; one without is taken as universal.
        return matchingKind.firstOrNull { it.name.contains(architecture, ignoreCase = true) }
            ?: matchingKind.singleOrNull { asset -> ARCHITECTURES.none { asset.name.contains(it, true) } }
    }

    internal val currentKind: InstallerKind
        get() {
            val os = System.getProperty("os.name").orEmpty().lowercase()
            return when {
                os.startsWith("mac") || os.startsWith("darwin") -> InstallerKind.Dmg
                os.startsWith("windows") -> InstallerKind.Msi
                else -> InstallerKind.Deb
            }
        }

    internal val currentUpdateKind: InstallerKind
        get() = if (currentKind == InstallerKind.Dmg) InstallerKind.Zip else currentKind

    internal val currentArchitecture: String
        get() = when (System.getProperty("os.arch").orEmpty().lowercase()) {
            "aarch64", "arm64" -> "arm64"
            else -> "x64"
        }

    private val ARCHITECTURES = listOf("arm64", "aarch64", "x64", "x86_64", "amd64")
}
