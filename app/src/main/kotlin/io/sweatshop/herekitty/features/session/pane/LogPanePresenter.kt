package io.sweatshop.herekitty.features.session.pane

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.sweatshop.herekitty.domain.features.logs.model.LogFilter
import io.sweatshop.herekitty.domain.features.logs.repository.LogSession
import io.sweatshop.herekitty.domain.features.logs.repository.LogViewSpec
import io.sweatshop.herekitty.domain.features.settings.repository.SettingsRepository
import io.sweatshop.herekitty.domain.features.views.model.LayoutOrientation
import io.sweatshop.herekitty.domain.features.views.model.PaneConfig
import io.sweatshop.herekitty.domain.features.views.model.PaneId
import io.sweatshop.herekitty.features.session.SplitSide
import io.sweatshop.herekitty.features.session.pane.LogPaneUiModel.Event.OnCloseClicked
import io.sweatshop.herekitty.features.session.pane.LogPaneUiModel.Event.OnCollapseDuplicatesToggled
import io.sweatshop.herekitty.features.session.pane.LogPaneUiModel.Event.OnFilterCleared
import io.sweatshop.herekitty.features.session.pane.LogPaneUiModel.Event.OnFollowTailToggled
import io.sweatshop.herekitty.features.session.pane.LogPaneUiModel.Event.OnMatchCaseToggled
import io.sweatshop.herekitty.features.session.pane.LogPaneUiModel.Event.OnMergeRequested
import io.sweatshop.herekitty.features.session.pane.LogPaneUiModel.Event.OnMinLevelChanged
import io.sweatshop.herekitty.features.session.pane.LogPaneUiModel.Event.OnQueryChanged
import io.sweatshop.herekitty.features.session.pane.LogPaneUiModel.Event.OnRegexToggled
import io.sweatshop.herekitty.features.session.pane.LogPaneUiModel.Event.OnScrolledUp
import io.sweatshop.herekitty.features.session.pane.LogPaneUiModel.Event.OnSplitPaneRequested
import io.sweatshop.herekitty.features.session.pane.LogPaneUiModel.Event.OnTagPickerDismissed
import io.sweatshop.herekitty.features.session.pane.LogPaneUiModel.Event.OnTagPickerOpened
import io.sweatshop.herekitty.features.session.pane.LogPaneUiModel.Event.OnTagSplitRequested
import io.sweatshop.herekitty.features.session.pane.LogPaneUiModel.Event.OnTagToggled
import io.sweatshop.herekitty.features.session.pane.line.effectiveColumnsFor
import io.sweatshop.herekitty.ui.presenter.EventHandler
import kotlinx.coroutines.delay
import org.koin.core.annotation.Factory


/**
 * The pane's filter lives in the [PaneConfig] its session was given, not here, so a setup can be
 * named, reapplied to another source, and shared. Only the editor buffer and the tag popup are local.
 */
@Factory
class LogPanePresenter(private val settingsRepository: SettingsRepository) {

    @Composable
    fun present(
        session: LogSession,
        config: PaneConfig,
        otherPanes: List<PaneConfig>,
        canClose: Boolean,
        onConfigChanged: (PaneConfig) -> Unit,
        onSplitTagOut: (String, LayoutOrientation, SplitSide) -> Unit,
        onSplitPane: (LayoutOrientation, SplitSide) -> Unit,
        onMergeInto: (PaneId) -> Unit,
        onClose: () -> Unit,
    ): LogPaneUiModel {
        val spec = remember(config.filter, config.collapseDuplicates) {
            LogViewSpec(filter = config.filter, collapseDuplicates = config.collapseDuplicates)
        }

        // Opened with the pane's own filter, so the history is indexed once rather than built empty
        // and immediately rebuilt.
        val view = remember(session) { session.openView(spec) }
        DisposableEffect(view) { onDispose { view.close() } }

        val revision by view.revision.collectAsState()
        val isRebuilding by view.isRebuilding.collectAsState()
        val availableTags by session.tags.collectAsState()
        val columns by settingsRepository.logColumns.collectAsState()
        val fontScale by settingsRepository.logFontScale.collectAsState()

        var queryInput by remember { mutableStateOf(config.filter.query) }
        var isTagPickerOpen by remember { mutableStateOf(false) }

        // Applying a saved view or clearing the filter changes the query underneath the editor.
        LaunchedEffect(config.filter.query) {
            if (queryInput != config.filter.query) queryInput = config.filter.query
        }

        // Rebuilding the match index walks the whole buffer, so wait for typing to settle first.
        LaunchedEffect(queryInput) {
            if (queryInput == config.filter.query) return@LaunchedEffect
            delay(QUERY_SETTLE_MILLIS)
            onConfigChanged(config.copy(filter = config.filter.copy(query = queryInput)))
        }

        LaunchedEffect(view, spec) { view.configure(spec) }

        val snapshot = remember(view, revision) { view.snapshot() }

        fun changeFilter(change: (LogFilter) -> LogFilter) {
            onConfigChanged(config.copy(filter = change(config.filter)))
        }

        return LogPaneUiModel(
            filter = config.filter,
            queryInput = queryInput,
            snapshot = snapshot,
            revision = revision,
            isRebuilding = isRebuilding,
            hasInvalidRegex = config.filter.useRegex &&
                config.filter.query.isNotBlank() &&
                !isValidRegex(config.filter.query),
            followTail = config.followTail,
            collapseDuplicates = config.collapseDuplicates,
            columns = effectiveColumnsFor(columns, config.filter),
            fontScale = fontScale,
            availableTags = availableTags,
            isTagPickerOpen = isTagPickerOpen,
            canClose = canClose,
            otherPanes = otherPanes,
            eventHandler = EventHandler(config.id) { event ->
                when (event) {
                    is OnQueryChanged -> queryInput = event.query

                    is OnTagSplitRequested ->
                        onSplitTagOut(event.tag, event.orientation, event.side)

                    is OnSplitPaneRequested -> onSplitPane(event.orientation, event.side)

                    is OnMergeRequested -> onMergeInto(event.targetId)

                    is OnTagToggled -> changeFilter { filter ->
                        val tags = if (event.tag in filter.tags) filter.tags - event.tag else filter.tags + event.tag
                        filter.copy(tags = tags)
                    }

                    is OnMinLevelChanged -> changeFilter { it.copy(minLevel = event.level) }
                    OnMatchCaseToggled -> changeFilter { it.copy(matchCase = !it.matchCase) }
                    OnRegexToggled -> changeFilter { it.copy(useRegex = !it.useRegex) }

                    OnTagPickerOpened -> isTagPickerOpen = true
                    OnTagPickerDismissed -> isTagPickerOpen = false

                    OnFollowTailToggled -> onConfigChanged(config.copy(followTail = !config.followTail))

                    OnCollapseDuplicatesToggled ->
                        onConfigChanged(config.copy(collapseDuplicates = !config.collapseDuplicates))
                    OnScrolledUp -> if (config.followTail) onConfigChanged(config.copy(followTail = false))

                    OnFilterCleared -> {
                        queryInput = ""
                        onConfigChanged(config.copy(filter = LogFilter()))
                    }

                    OnCloseClicked -> onClose()
                }
            },
        )
    }

    private fun isValidRegex(pattern: String): Boolean = runCatching { Regex(pattern) }.isSuccess

    private companion object {
        const val QUERY_SETTLE_MILLIS = 250L
    }
}
