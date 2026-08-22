package io.sweatshop.herekitty.updates

import io.sweatshop.herekitty.domain.features.updates.model.AppVersion
import io.sweatshop.herekitty.domain.features.updates.model.ReleaseAsset
import io.sweatshop.herekitty.domain.features.updates.model.LatestRelease
import io.sweatshop.herekitty.domain.features.updates.model.ReleaseInfo
import io.sweatshop.herekitty.domain.features.updates.model.UpdateState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class UpdateCheckTest {

    private val running = AppVersion(1, 0, 0)
    private val installer = ReleaseAsset("HereKitty-1.1.0-macos-arm64.zip", "https://example/x", 1L)

    private fun release(version: String) =
        ReleaseInfo(AppVersion.parse(version)!!, listOf(installer), "https://example/notes")

    private fun found(version: String) = LatestRelease.Found(release(version))

    @Test
    fun `a newer release with an installer is an offer`() {
        val outcome = UpdateCheck.outcomeOf(running, found("1.1.0"), installer)

        assertEquals(UpdateState.Available(release("1.1.0")), outcome)
    }

    @Test
    fun `the same version is not an update`() {
        assertEquals(UpdateState.UpToDate(running), UpdateCheck.outcomeOf(running, found("1.0.0"), installer))
    }

    /** A published release older than what is installed must never be offered as an update. */
    @Test
    fun `an older release is not offered as a downgrade`() {
        assertEquals(UpdateState.UpToDate(running), UpdateCheck.outcomeOf(running, found("0.9.0"), installer))
        assertEquals(
            UpdateState.UpToDate(AppVersion(2, 0, 0)),
            UpdateCheck.outcomeOf(AppVersion(2, 0, 0), found("1.9.9"), installer),
        )
    }

    /** Reporting up to date here would look like the check was broken. */
    @Test
    fun `a newer release with no build for this platform says so`() {
        val outcome = UpdateCheck.outcomeOf(running, found("1.1.0"), installer = null)

        assertIs<UpdateState.Failed>(outcome)
        assertTrue("1.1.0" in outcome.reason, outcome.reason)
        assertTrue("platform" in outcome.reason, outcome.reason)
    }

    @Test
    fun `an unreachable release is a failure`() {
        val outcome = UpdateCheck.outcomeOf(running, LatestRelease.Unreachable("GitHub answered 500"), null)

        assertIs<UpdateState.Failed>(outcome)
        assertTrue("500" in outcome.reason, outcome.reason)
    }

    /**
     * A project that has published nothing is not a broken check — there is genuinely nothing newer,
     * which is the state this repository is in until its first release.
     */
    @Test
    fun `nothing published yet reads as up to date, not as a failure`() {
        assertEquals(UpdateState.UpToDate(running), UpdateCheck.outcomeOf(running, LatestRelease.None, null))
    }

    /** Running a candidate should still be offered the release it precedes. */
    @Test
    fun `a pre-release is offered the final release`() {
        val outcome = UpdateCheck.outcomeOf(AppVersion.parse("1.0.0-rc1")!!, found("1.0.0"), installer)

        assertIs<UpdateState.Available>(outcome)
    }

    @Test
    fun `a pre-release newer than the running release is an offer`() {
        assertIs<UpdateState.Available>(UpdateCheck.outcomeOf(running, found("1.1.0-rc1"), installer))
    }
}
