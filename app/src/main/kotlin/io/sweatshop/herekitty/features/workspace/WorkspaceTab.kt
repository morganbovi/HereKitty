package io.sweatshop.herekitty.features.workspace

import io.sweatshop.herekitty.domain.features.logs.model.SessionSource
import io.sweatshop.herekitty.domain.features.views.model.ViewConfig
import io.sweatshop.herekitty.ui.format.truncateMiddle

@JvmInline
value class TabId(val value: Long)

data class WorkspaceTab(val id: TabId, val root: WorkspaceNode) {
    /**
     * Named after the view it is running, not the device it is pointed at: the device already shows in
     * the session's own toolbar, and has to, since a split tab can hold several. An opened file with no
     * named view falls back to its own file name rather than a bare "New tab" — a live device gets no
     * such fallback, since one unnamed device tab looks the same as any other regardless.
     *
     * Ambiguity between tabs is resolved later, across the whole list, by [disambiguateTitles].
     */
    val baseTitle: String
        get() {
            val viewNames = root.slots().map { it.view.name.trim() }.filter { it.isNotEmpty() }.distinct()
            if (viewNames.isNotEmpty()) return joinTitleParts(viewNames)

            val fileNames = root.slots()
                .mapNotNull { (it.session?.source?.value as? SessionSource.Recording)?.detail }
                .filter { it.isNotEmpty() }
                .distinct()
                .map { truncateMiddle(it) }
            return joinTitleParts(fileNames)
        }

    private fun joinTitleParts(parts: List<String>): String = when {
        parts.isEmpty() -> UNNAMED_TITLE
        parts.size <= MAX_TITLE_VIEWS -> parts.joinToString(" + ")
        else -> "${parts.take(MAX_TITLE_VIEWS).joinToString(" + ")} +${parts.size - MAX_TITLE_VIEWS}"
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
