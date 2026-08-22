package io.sweatshop.herekitty.features.updates

import io.sweatshop.herekitty.domain.features.updates.model.AppVersion
import io.sweatshop.herekitty.domain.features.updates.model.ReleaseInfo
import io.sweatshop.herekitty.domain.features.updates.model.UpdateState
import io.sweatshop.herekitty.ui.presenter.EventHandler
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** When the corner notice appears, which is the difference between helpful and nagging. */
class UpdateUiModelTest {

    private val release = ReleaseInfo(AppVersion(1, 1, 0), emptyList(), "https://example/notes")

    private fun model(state: UpdateState, wasAsked: Boolean) = UpdateUiModel(
        state = state,
        runningVersion = AppVersion(1, 0, 0),
        wasAsked = wasAsked,
        eventHandler = EventHandler {},
    )

    @Test
    fun `an available update always shows, asked for or not`() {
        assertTrue(model(UpdateState.Available(release), wasAsked = false).isWorthShowing)
        assertTrue(model(UpdateState.Available(release), wasAsked = true).isWorthShowing)
    }

    @Test
    fun `work in progress always shows`() {
        assertTrue(model(UpdateState.Downloading(release, 0.5f), wasAsked = false).isWorthShowing)
        assertTrue(model(UpdateState.ReadyToInstall(release, "/tmp/x"), wasAsked = false).isWorthShowing)
    }

    /** Telling someone they are up to date every single launch is noise, not information. */
    @Test
    fun `up to date only shows when it answers a question`() {
        assertFalse(model(UpdateState.UpToDate(AppVersion(1, 0, 0)), wasAsked = false).isWorthShowing)
        assertTrue(model(UpdateState.UpToDate(AppVersion(1, 0, 0)), wasAsked = true).isWorthShowing)
    }

    /**
     * A failed background check stays quiet: being offline on launch is not something to interrupt
     * for. A failed check someone pressed a button for does need an answer.
     */
    @Test
    fun `a failure only shows when it answers a question`() {
        assertFalse(model(UpdateState.Failed("offline"), wasAsked = false).isWorthShowing)
        assertTrue(model(UpdateState.Failed("offline"), wasAsked = true).isWorthShowing)
    }

    @Test
    fun `idle shows nothing either way`() {
        assertFalse(model(UpdateState.Idle, wasAsked = false).isWorthShowing)
        assertFalse(model(UpdateState.Idle, wasAsked = true).isWorthShowing)
    }

    @Test
    fun `checking shows only when asked, so a launch is silent`() {
        assertFalse(model(UpdateState.Checking, wasAsked = false).isWorthShowing)
        assertTrue(model(UpdateState.Checking, wasAsked = true).isWorthShowing)
    }
}
