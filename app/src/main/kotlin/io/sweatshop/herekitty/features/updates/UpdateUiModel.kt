package io.sweatshop.herekitty.features.updates

import io.sweatshop.herekitty.domain.features.updates.model.AppVersion
import io.sweatshop.herekitty.domain.features.updates.model.UpdateState
import io.sweatshop.herekitty.ui.presenter.EventHandler

data class UpdateUiModel(
    val state: UpdateState,
    val runningVersion: AppVersion,
    /** True once the user asked, which is what makes "you are up to date" worth showing. */
    val wasAsked: Boolean,
    val eventHandler: EventHandler<Event>,
) {
    /**
     * A launch that finds nothing says nothing. Confirming "up to date" every time the app opens is
     * noise; confirming it when someone pressed the button is the answer to a question.
     */
    val isWorthShowing: Boolean
        get() = when (state) {
            is UpdateState.Available, is UpdateState.Downloading, is UpdateState.ReadyToInstall -> true
            is UpdateState.Failed, is UpdateState.UpToDate -> wasAsked
            is UpdateState.Checking -> wasAsked
            UpdateState.Idle -> false
        }

    sealed interface Event {
        data object OnCheckRequested : Event

        data object OnDownloadRequested : Event

        data object OnRestartRequested : Event

        data object OnDismissed : Event
    }
}
