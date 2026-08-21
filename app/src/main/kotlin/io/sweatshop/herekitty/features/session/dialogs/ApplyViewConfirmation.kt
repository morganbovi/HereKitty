package io.sweatshop.herekitty.features.session.dialogs

import io.sweatshop.herekitty.domain.features.views.model.ViewConfig

/**
 * Whether applying [incoming] should stop and ask first.
 *
 * Applying a view replaces the whole arrangement, so the question is only worth asking when there is
 * something to lose. A single pane is not an arrangement, and a setup already saved under its own name
 * is one click away again — only unsaved work can actually be lost.
 */
internal fun shouldConfirmApply(
    current: ViewConfig,
    incoming: ViewConfig,
    hasUnsavedChanges: Boolean,
): Boolean = hasUnsavedChanges &&
    current.panes.size > 1 &&
    !current.hasSameSetupAs(incoming)
