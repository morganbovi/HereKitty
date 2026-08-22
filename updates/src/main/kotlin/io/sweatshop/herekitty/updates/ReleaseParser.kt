package io.sweatshop.herekitty.updates

import io.sweatshop.herekitty.domain.features.updates.model.AppVersion
import io.sweatshop.herekitty.domain.features.updates.model.ReleaseAsset
import io.sweatshop.herekitty.domain.features.updates.model.ReleaseInfo
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Reads GitHub's release JSON.
 *
 * `ignoreUnknownKeys` because the response carries dozens of fields this cares nothing about, and
 * GitHub adds more over time; failing to parse a release because of a new field would take the
 * updater down for something irrelevant.
 */
object ReleaseParser {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Null when the payload is not a release this app can act on — an unparseable tag, or no assets.
     * A release whose tag makes no sense must not become an update offer.
     */
    fun parseLatest(body: String): ReleaseInfo? {
        val release = runCatching { json.decodeFromString<GithubRelease>(body) }.getOrNull() ?: return null
        if (release.draft) return null

        val version = AppVersion.parse(release.tagName) ?: return null

        return ReleaseInfo(
            version = version,
            assets = release.assets.map { ReleaseAsset(it.name, it.downloadUrl, it.size) },
            notesUrl = release.htmlUrl,
        )
    }

    @Serializable
    private data class GithubRelease(
        @SerialName("tag_name") val tagName: String,
        @SerialName("html_url") val htmlUrl: String = "",
        val draft: Boolean = false,
        val prerelease: Boolean = false,
        val assets: List<GithubAsset> = emptyList(),
    )

    @Serializable
    private data class GithubAsset(
        val name: String,
        @SerialName("browser_download_url") val downloadUrl: String,
        val size: Long = 0L,
    )
}
