package io.sweatshop.herekitty.updates

import io.sweatshop.herekitty.domain.features.updates.model.AppVersion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Shaped like a real `/releases/latest` response, trimmed to the fields that are read. */
class ReleaseParserTest {

    private val body = """
        {
          "tag_name": "v1.1.0",
          "html_url": "https://github.com/morganbovi/HereKitty/releases/tag/v1.1.0",
          "draft": false,
          "prerelease": false,
          "created_at": "2026-08-21T00:00:00Z",
          "author": { "login": "bovi", "id": 1 },
          "assets": [
            {
              "name": "HereKitty-1.1.0-macos-arm64.dmg",
              "browser_download_url": "https://github.com/x/releases/download/v1.1.0/HereKitty-1.1.0-macos-arm64.dmg",
              "size": 92274688,
              "content_type": "application/octet-stream",
              "download_count": 3
            },
            {
              "name": "SHA256SUMS",
              "browser_download_url": "https://github.com/x/releases/download/v1.1.0/SHA256SUMS",
              "size": 214
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `reads the version, assets and notes url`() {
        val release = ReleaseParser.parseLatest(body)!!

        assertEquals(AppVersion(1, 1, 0), release.version)
        assertEquals(2, release.assets.size)
        assertEquals("HereKitty-1.1.0-macos-arm64.dmg", release.assets.first().name)
        assertEquals(92274688L, release.assets.first().sizeBytes)
        assertEquals("https://github.com/morganbovi/HereKitty/releases/tag/v1.1.0", release.notesUrl)
    }

    /** GitHub adds response fields over time; a new one must not take the updater down. */
    @Test
    fun `unknown fields are ignored`() {
        val withExtras = body.replace("\"draft\": false,", "\"draft\": false, \"something_new\": {\"a\": 1},")

        assertEquals(AppVersion(1, 1, 0), ReleaseParser.parseLatest(withExtras)?.version)
    }

    @Test
    fun `a draft is not an update`() {
        assertNull(ReleaseParser.parseLatest(body.replace("\"draft\": false", "\"draft\": true")))
    }

    /** A tag nobody can order must not become an offer to "update". */
    @Test
    fun `a release whose tag is not a version is refused`() {
        assertNull(ReleaseParser.parseLatest(body.replace("\"tag_name\": \"v1.1.0\"", "\"tag_name\": \"nightly\"")))
    }

    @Test
    fun `malformed or empty payloads are refused rather than throwing`() {
        assertNull(ReleaseParser.parseLatest(""))
        assertNull(ReleaseParser.parseLatest("not json"))
        assertNull(ReleaseParser.parseLatest("{}"))
        assertNull(ReleaseParser.parseLatest("""{"message":"Not Found"}"""))
    }

    @Test
    fun `a release with no assets still parses, with none`() {
        val bare = """{"tag_name":"v2.0.0","html_url":"u","draft":false,"assets":[]}"""

        assertEquals(emptyList(), ReleaseParser.parseLatest(bare)?.assets)
    }
}
