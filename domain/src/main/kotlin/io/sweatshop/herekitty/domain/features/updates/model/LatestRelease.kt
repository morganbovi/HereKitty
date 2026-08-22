package io.sweatshop.herekitty.domain.features.updates.model

/**
 * What asking about the newest release turned up.
 *
 * [None] is separate from [Unreachable] because a project that has published nothing yet is not a
 * failure — the app genuinely is up to date, and reporting "could not check" there would be wrong.
 */
sealed interface LatestRelease {
    data class Found(val release: ReleaseInfo) : LatestRelease

    data object None : LatestRelease

    data class Unreachable(val reason: String) : LatestRelease
}
