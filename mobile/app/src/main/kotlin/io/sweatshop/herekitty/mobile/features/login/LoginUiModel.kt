package io.sweatshop.herekitty.mobile.features.login

import io.sweatshop.herekitty.mobile.ui.presenter.EventHandler

data class LoginUiModel(
    val isLoading: Boolean,
    val error: String?,
    val signedInUserId: String?,
    val eventHandler: EventHandler<Event>,
) {
    sealed interface Event {
        data object OnSignInClicked : Event
        data object OnSignOutClicked : Event
        data object OnErrorDismissed : Event
    }
}
