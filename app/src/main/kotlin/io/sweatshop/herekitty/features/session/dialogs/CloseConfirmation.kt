package io.sweatshop.herekitty.features.session.dialogs

import io.sweatshop.herekitty.domain.features.views.model.ViewConfig

/**
 * Whether closing a session should stop and ask.
 *
 * Only a view that has been *named* is worth protecting: a setup you never named is a scratch pad, and
 * being asked to name it every time you close a session is nagging rather than helping.
 */
internal fun shouldConfirmClose(
    view: ViewConfig,
    hasUnsavedChanges: Boolean,
    askingEnabled: Boolean,
): Boolean = askingEnabled && hasUnsavedChanges && view.name.isNotBlank()
