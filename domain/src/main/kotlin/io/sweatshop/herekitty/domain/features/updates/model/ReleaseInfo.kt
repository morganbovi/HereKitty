package io.sweatshop.herekitty.domain.features.updates.model

/**
 * A published release, reduced to what an update needs.
 *
 * Holds every asset rather than the one for this machine, because which asset applies is a decision
 * about where the app is running, not about what was released — and keeping them all is what lets one
 * captured response be tested against every platform.
 */
data class ReleaseInfo(
    val version: AppVersion,
    val assets: List<ReleaseAsset>,
    val notesUrl: String,
)

data class ReleaseAsset(val name: String, val downloadUrl: String, val sizeBytes: Long)

/**
 * What a release can carry.
 *
 * [Zip] is not something jpackage produces — it is the app bundle archived alongside the `.dmg`,
 * because replacing an installed app in place needs the bundle itself. Unpacking a zip is one step;
 * getting a bundle out of a disk image means mounting it, copying, and unmounting, with a mount point
 * to leak if anything fails in between. It is the same split Sparkle makes: disk images for people,
 * archives for updaters.
 */
enum class InstallerKind(val extension: String) {
    Dmg("dmg"),
    Zip("zip"),
    Msi("msi"),
    Deb("deb"),
}
