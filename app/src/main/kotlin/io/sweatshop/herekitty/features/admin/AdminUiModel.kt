package io.sweatshop.herekitty.features.admin

import io.sweatshop.herekitty.ui.presenter.EventHandler

data class AdminUiModel(
    val roleIsAdmin: Boolean,
    val isSubmitting: Boolean,
    val resultMessage: String?,
    val error: String?,
    val eventHandler: EventHandler<Event>,
) {
    sealed interface Event {
        data class OnRoleIsAdminChanged(val isAdmin: Boolean) : Event
        data class OnSubmitClicked(val email: String) : Event
        data object OnResultDismissed : Event
        data object OnCloseClicked : Event
    }
}
