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

/** The installer kinds jpackage produces, and so the ones a release can carry. */
enum class InstallerKind(val extension: String) {
    Dmg("dmg"),
    Msi("msi"),
    Deb("deb"),
}
