package io.sweatshop.herekitty.features.workspace

import io.sweatshop.herekitty.domain.features.views.model.ViewConfig

@JvmInline
value class TabId(val value: Long)

data class WorkspaceTab(val id: TabId, val root: WorkspaceNode) {
    /**
     * Named after the view it is running, not the device it is pointed at: the device already shows in
     * the session's own toolbar, and has to, since a split tab can hold several.
     *
     * Ambiguity between tabs is resolved later, across the whole list, by [disambiguateTitles].
     */
    val baseTitle: String
        get() {
            val viewNames = root.slots().map { it.view.name.trim() }.filter { it.isNotEmpty() }.distinct()
            return when {
                viewNames.isEmpty() -> UNNAMED_TITLE
                viewNames.size <= MAX_TITLE_VIEWS -> viewNames.joinToString(" + ")
                else -> "${viewNames.take(MAX_TITLE_VIEWS).joinToString(" + ")} +${viewNames.size - MAX_TITLE_VIEWS}"
            }
        }

    /**
     * Whether there is anything in here worth closing: a session, a split, or a view someone set up.
     *
     * Closing the *last* tab is how a blank slate is asked for, so it is allowed as long as this is
     * true. An already-empty tab has nothing to close and keeps its cross hidden.
     */
    val hasContent: Boolean
        get() = root.slots().let { slots ->
            slots.size > 1 ||
                slots.any { it.session != null || !it.view.hasSameSetupAs(ViewConfig.Default) }
        }

    internal companion object {
        const val UNNAMED_TITLE = "New tab"
        private const val MAX_TITLE_VIEWS = 2
    }
}
