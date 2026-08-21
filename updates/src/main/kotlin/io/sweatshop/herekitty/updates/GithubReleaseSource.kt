package io.sweatshop.herekitty.updates

import io.sweatshop.herekitty.domain.base.Log
import io.sweatshop.herekitty.domain.features.updates.model.ReleaseInfo
import io.sweatshop.herekitty.net.HttpFetch

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

    suspend fun latest(): ReleaseInfo? {
        val url = "https://api.github.com/repos/$slug/releases/latest"

        val body = runCatching { HttpFetch.text(url) }
            .onFailure { Log.w(it) { "Could not reach $url" } }
            .getOrNull()
            ?: return null

        return ReleaseParser.parseLatest(body)
            .also { if (it == null) Log.w { "$url returned nothing this app can update to" } }
    }

    companion object {
        const val DEFAULT_SLUG = "morganbovi/HereKitty"

        /** Overridable so a test can point at another repository without a code change. */
        fun fromEnvironment(): GithubReleaseSource =
            GithubReleaseSource(System.getenv("HEREKITTY_RELEASES_REPO") ?: DEFAULT_SLUG)
    }
}
