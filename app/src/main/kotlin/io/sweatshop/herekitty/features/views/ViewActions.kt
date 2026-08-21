package io.sweatshop.herekitty.features.views

import io.sweatshop.herekitty.domain.features.views.model.ViewConfig

/** What the view menu can do. Bundled because these always travel together down to a session. */
data class ViewActions(
    val savedViews: List<ViewConfig>,
    val onApply: (ViewConfig) -> Unit,
    val onSave: (ViewConfig) -> Unit,
    val onDelete: (String) -> Unit,
    val onExport: (ViewConfig) -> Unit,
    val onImport: () -> Unit,
)
