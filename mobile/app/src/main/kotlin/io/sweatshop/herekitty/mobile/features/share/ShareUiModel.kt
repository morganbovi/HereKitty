package io.sweatshop.herekitty.mobile.features.share

import io.sweatshop.herekitty.mobile.domain.devices.Device
import io.sweatshop.herekitty.mobile.ui.presenter.EventHandler

data class ShareUiModel(
    val device: Device?,
    val nameDraft: String,
    val editingName: Boolean,
    val bridgeStatus: String,
    val error: String?,
    val eventHandler: EventHandler<Event>,
) {
    sealed interface Event {
        data class OnSharedToggled(val shared: Boolean) : Event
        data class OnNameChanged(val name: String) : Event
        data object OnNameSaved : Event
        data object OnNameEditStarted : Event
        data object OnNameEditCancelled : Event
        data object OnRetryClicked : Event
        data object OnErrorDismissed : Event
    }
}
