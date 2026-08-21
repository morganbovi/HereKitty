package io.sweatshop.herekitty.updates

import io.sweatshop.herekitty.domain.base.Log
import io.sweatshop.herekitty.domain.features.updates.model.LatestRelease
import io.sweatshop.herekitty.net.HttpFetch
import io.sweatshop.herekitty.net.HttpStatusException

/**
 * The one GitHub call this app makes.
 *
 * Unauthenticated on purpose: the repository is public, so `releases/latest` needs no token, and the
 * 60-requests-an-hour limit is far above one check per launch. Shipping a token would be worse than
 * pointless — it would be a credential in a public binary.
 *
 * `releases/latest` rather than the full list because it already excludes drafts and pre-releases.
 */
class GithubReleaseSource(private val slug: String) {

    suspend fun latest(): LatestRelease {
        val url = "https://api.github.com/repos/$slug/releases/latest"

        val body = try {
            HttpFetch.text(url)
        } catch (e: HttpStatusException) {
            // GitHub answers 404 both for a repository with no releases and for one it will not show
            // us. Either way there is nothing to update to, and that is not a failure to report.
            return if (e.code == NOT_FOUND) {
                LatestRelease.None
            } else {
                LatestRelease.Unreachable("GitHub answered ${e.code}")
            }
        } catch (e: Exception) {
            Log.w(e) { "Could not reach $url" }
            return LatestRelease.Unreachable(e.message ?: "Could not reach GitHub")
        }

        val release = ReleaseParser.parseLatest(body)
            ?: return LatestRelease.Unreachable("The latest release is not one this app can install")

        return LatestRelease.Found(release)
    }

    companion object {
        private const val NOT_FOUND = 404

        const val DEFAULT_SLUG = "morganbovi/HereKitty"

        /** Overridable so a test can point at another repository without a code change. */
        fun fromEnvironment(): GithubReleaseSource =
            GithubReleaseSource(System.getenv("HEREKITTY_RELEASES_REPO") ?: DEFAULT_SLUG)
    }
}
