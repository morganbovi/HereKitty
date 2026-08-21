package io.sweatshop.herekitty.updates

import io.sweatshop.herekitty.domain.features.updates.model.AppVersion
import io.sweatshop.herekitty.domain.features.updates.model.ReleaseAsset
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

    @Test
    fun `a newer release with an installer is an offer`() {
        val outcome = UpdateCheck.outcomeOf(running, release("1.1.0"), installer)

        assertEquals(UpdateState.Available(release("1.1.0")), outcome)
    }

    @Test
    fun `the same version is not an update`() {
        assertEquals(UpdateState.UpToDate(running), UpdateCheck.outcomeOf(running, release("1.0.0"), installer))
    }

    /** A published release older than what is installed must never be offered as an update. */
    @Test
    fun `an older release is not offered as a downgrade`() {
        assertEquals(UpdateState.UpToDate(running), UpdateCheck.outcomeOf(running, release("0.9.0"), installer))
        assertEquals(
            UpdateState.UpToDate(AppVersion(2, 0, 0)),
            UpdateCheck.outcomeOf(AppVersion(2, 0, 0), release("1.9.9"), installer),
        )
    }

    /** Reporting up to date here would look like the check was broken. */
    @Test
    fun `a newer release with no build for this platform says so`() {
        val outcome = UpdateCheck.outcomeOf(running, release("1.1.0"), installer = null)

        assertIs<UpdateState.Failed>(outcome)
        assertTrue("1.1.0" in outcome.reason, outcome.reason)
        assertTrue("platform" in outcome.reason, outcome.reason)
    }

    @Test
    fun `an unreachable or unreadable release is a failure, not up to date`() {
        val outcome = UpdateCheck.outcomeOf(running, release = null, installer = null)

        assertIs<UpdateState.Failed>(outcome)
    }

    /** Running a candidate should still be offered the release it precedes. */
    @Test
    fun `a pre-release is offered the final release`() {
        val outcome = UpdateCheck.outcomeOf(AppVersion.parse("1.0.0-rc1")!!, release("1.0.0"), installer)

        assertIs<UpdateState.Available>(outcome)
    }

    @Test
    fun `a pre-release newer than the running release is an offer`() {
        assertIs<UpdateState.Available>(UpdateCheck.outcomeOf(running, release("1.1.0-rc1"), installer))
    }
}
