package io.sweatshop.herekitty.features.session.pane

import io.sweatshop.herekitty.domain.features.logs.model.LogFilter
import io.sweatshop.herekitty.domain.features.logs.model.LogLevel
import io.sweatshop.herekitty.domain.features.logs.model.TagStats
import io.sweatshop.herekitty.domain.features.logs.repository.LogSnapshot
import io.sweatshop.herekitty.domain.features.views.model.LayoutOrientation
import io.sweatshop.herekitty.domain.features.views.model.PaneConfig
import io.sweatshop.herekitty.domain.features.views.model.PaneId
import io.sweatshop.herekitty.features.session.SplitSide
import io.sweatshop.herekitty.domain.features.settings.model.LogColumns
import io.sweatshop.herekitty.ui.presenter.EventHandler

data class LogPaneUiModel(
    val filter: LogFilter,
    val queryInput: String,
    val snapshot: LogSnapshot,
    val revision: Long,
    val isRebuilding: Boolean,
    val hasInvalidRegex: Boolean,
    val followTail: Boolean,
    val collapseDuplicates: Boolean,
    val columns: LogColumns,
    val fontScale: Float,
    val availableTags: List<TagStats>,
    val isTagPickerOpen: Boolean,
    val canClose: Boolean,
    val otherPanes: List<PaneConfig>,
    val eventHandler: EventHandler<Event>,
) {
    val matchCount: Int get() = snapshot.size

    val hasActiveFilter: Boolean get() = !filter.isPassThrough

    /** The level dropdown's "Crashes" entry is really this tag, not a level. */
    val isCrashesFilterActive: Boolean get() = filter.tags == CRASH_TAGS

    /** A tag can only be pulled out when the pane is watching more than one. */
    val canSplitTags: Boolean get() = filter.tags.size > 1

    val canMerge: Boolean get() = otherPanes.isNotEmpty()

    /** What this pane is narrowed to, spelled out so the AND between the parts is visible. */
    val filterSummary: String
        get() = buildList {
            if (isCrashesFilterActive) {
                add("Crashes")
            } else if (filter.tags.isNotEmpty()) {
                add(filter.tags.joinToString(", ") { it.trim() })
            }
            if (filter.query.isNotBlank()) add("\"${filter.query}\"")
            if (filter.minLevel != LogLevel.VERBOSE) add("${filter.minLevel.letter} and above")
        }.joinToString(" and ")

    val searchPlaceholder: String
        get() = when {
            filter.tags.size == 1 -> "Search within this tag"
            filter.tags.size > 1 -> "Search within these ${filter.tags.size} tags"
            else -> "Search all lines"
        }

    sealed interface Event {
        data class OnQueryChanged(val query: String) : Event

        data class OnTagToggled(val tag: String) : Event

        data class OnTagSplitRequested(
            val tag: String,
            val orientation: LayoutOrientation,
            val side: SplitSide,
        ) : Event

        data class OnSplitPaneRequested(
            val orientation: LayoutOrientation,
            val side: SplitSide,
        ) : Event

        data class OnMergeRequested(val targetId: PaneId) : Event

        data class OnMinLevelChanged(val level: LogLevel) : Event

        data object OnMatchCaseToggled : Event

        data object OnRegexToggled : Event

        data object OnTagPickerOpened : Event

        data object OnTagPickerDismissed : Event

        data object OnFollowTailToggled : Event

        data object OnCollapseDuplicatesToggled : Event

        data object OnScrolledUp : Event

        data object OnFilterCleared : Event

        data object OnCrashesFilterSelected : Event

        data object OnCloseClicked : Event
    }

    companion object {
        /** Real crashes are logged by the platform's own runtime under this exact tag. */
        val CRASH_TAGS = setOf("AndroidRuntime")
    }
}
