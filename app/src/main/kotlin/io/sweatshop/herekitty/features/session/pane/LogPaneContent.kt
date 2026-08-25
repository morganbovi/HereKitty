package io.sweatshop.herekitty.features.session.pane

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.PointerMatcher
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.onClick
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.sweatshop.herekitty.domain.features.logs.model.LogLevel
import io.sweatshop.herekitty.domain.features.logs.model.LogLine
import io.sweatshop.herekitty.domain.features.logs.model.SessionEvent
import io.sweatshop.herekitty.domain.features.logs.repository.LogSession
import io.sweatshop.herekitty.domain.features.logs.repository.LogSnapshot
import io.sweatshop.herekitty.domain.features.settings.model.LogColumns
import io.sweatshop.herekitty.domain.features.settings.model.LogLineLayout
import io.sweatshop.herekitty.domain.features.views.model.LayoutOrientation
import io.sweatshop.herekitty.domain.features.views.model.PaneConfig
import io.sweatshop.herekitty.domain.features.views.model.PaneId
import io.sweatshop.herekitty.features.session.SplitSide
import io.sweatshop.herekitty.features.session.pane.LogPaneUiModel.Event.OnCloseClicked
import io.sweatshop.herekitty.features.session.pane.LogPaneUiModel.Event.OnCollapseDuplicatesToggled
import io.sweatshop.herekitty.features.session.pane.LogPaneUiModel.Event.OnCrashesFilterSelected
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
import io.sweatshop.herekitty.features.session.pane.line.MetadataPart
import io.sweatshop.herekitty.features.session.pane.line.needsMetadataHeader
import io.sweatshop.herekitty.features.session.pane.line.stackedMetadataParts
import io.sweatshop.herekitty.ui.component.IconAction
import io.sweatshop.herekitty.ui.component.ToggleableIconAction
import io.sweatshop.herekitty.ui.format.formatCount
import io.sweatshop.herekitty.ui.format.formatTimeOfDay
import io.sweatshop.herekitty.ui.split.ReorderGrip
import io.sweatshop.herekitty.ui.theme.colorFor
import java.awt.Cursor
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.ContextMenuItemOption
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.HorizontalScrollbar
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.ListComboBox
import org.jetbrains.jewel.ui.component.PopupContainer
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.component.VerticalScrollbar
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.ui.typography
import org.koin.compose.koinInject


private val LEVEL_LABELS = LogLevel.entries.map { it.label }
private val LEVEL_DROPDOWN_ITEMS = LEVEL_LABELS + "Crashes"

@Composable
fun LogPaneContent(
    session: LogSession,
    config: PaneConfig,
    otherPanes: List<PaneConfig>,
    canClose: Boolean,
    onConfigChanged: (PaneConfig) -> Unit,
    onSplitTagOut: (String, LayoutOrientation, SplitSide) -> Unit,
    onSplitPane: (LayoutOrientation, SplitSide) -> Unit,
    onMergeInto: (PaneId) -> Unit,
    onClose: () -> Unit,
    grip: ReorderGrip = ReorderGrip.Disabled,
    tagGrip: @Composable (tag: String) -> Modifier = { Modifier },
    modifier: Modifier = Modifier,
    presenter: LogPanePresenter = koinInject(),
) {
    val uiModel = presenter.present(
        session = session,
        config = config,
        otherPanes = otherPanes,
        canClose = canClose,
        onConfigChanged = onConfigChanged,
        onSplitTagOut = onSplitTagOut,
        onSplitPane = onSplitPane,
        onMergeInto = onMergeInto,
        onClose = onClose,
    )

    Column(modifier.fillMaxSize()) {
        PaneToolbar(uiModel, grip)
        SearchRow(uiModel)
        if (uiModel.filter.tags.isNotEmpty() && !uiModel.isCrashesFilterActive) SelectedTagRow(uiModel, tagGrip)
        Divider(Orientation.Horizontal)
        LogLines(uiModel, Modifier.weight(1f))
        Divider(Orientation.Horizontal)
        PaneStatusBar(uiModel)
    }
}

@Composable
private fun PaneToolbar(uiModel: LogPaneUiModel, grip: ReorderGrip) {
    var isSplitMenuOpen by remember { mutableStateOf(false) }
    var isMergeMenuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(TOOLBAR_HEIGHT)
            .background(
                if (grip.isDragging) {
                    JewelTheme.globalColors.outlines.focused.copy(alpha = 0.18f)
                } else {
                    Color.Transparent
                },
            )
            .padding(horizontal = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (grip.isEnabled) {
            Box(
                modifier = grip.modifier.pointerHoverIcon(MOVE_CURSOR),
                contentAlignment = Alignment.Center,
            ) {
                Icon(AllIconsKeys.General.Drag, contentDescription = "Drag to reorder this pane")
            }
        }

        if (!uiModel.isCrashesFilterActive) {
            Box {
                Tooltip(tooltip = { Text("Filter by tags seen this session") }) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .clickable { uiModel.eventHandler(OnTagPickerOpened) }
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Icon(AllIconsKeys.Nodes.Tag, contentDescription = "Tags")
                        Text(
                            text = uiModel.filter.describesTags.ifEmpty { "All tags" },
                            style = JewelTheme.typography.small,
                            color = if (uiModel.filter.tags.isEmpty()) {
                                JewelTheme.globalColors.text.info
                            } else {
                                JewelTheme.globalColors.text.normal
                            },
                            maxLines = 1,
                            softWrap = false,
                        )
                        Icon(AllIconsKeys.General.ChevronDown, contentDescription = null)
                    }
                }

                if (uiModel.isTagPickerOpen) {
                    TagPickerPopup(
                        tags = uiModel.availableTags,
                        selectedTags = uiModel.filter.tags,
                        onTagToggled = { uiModel.eventHandler(OnTagToggled(it)) },
                        onDismissRequest = { uiModel.eventHandler(OnTagPickerDismissed) },
                    )
                }
            }
        }

        Box(Modifier.weight(1f))

        Tooltip(tooltip = { Text("Minimum level, or crashes only") }) {
            ListComboBox(
                items = LEVEL_DROPDOWN_ITEMS,
                selectedIndex = if (uiModel.isCrashesFilterActive) LEVEL_LABELS.size else uiModel.filter.minLevel.ordinal,
                onSelectedItemChange = { index ->
                    if (index == LEVEL_LABELS.size) {
                        uiModel.eventHandler(OnCrashesFilterSelected)
                    } else {
                        uiModel.eventHandler(OnMinLevelChanged(LogLevel.entries[index]))
                    }
                },
                modifier = Modifier.width(LEVEL_SELECTOR_WIDTH),
            )
        }

        IconAction(
            key = AllIconsKeys.Actions.Cancel,
            description = "Clear this pane's filter",
            enabled = uiModel.hasActiveFilter,
            onClick = { uiModel.eventHandler(OnFilterCleared) },
        )

        Box {
            IconAction(
                key = AllIconsKeys.Actions.SplitVertically,
                description = "Split this pane",
                onClick = { isSplitMenuOpen = true },
            )

            if (isSplitMenuOpen) {
                SplitMenu(uiModel) { isSplitMenuOpen = false }
            }
        }

        Box {
            IconAction(
                key = AllIconsKeys.General.CollapseComponent,
                description = "Merge this pane into another",
                enabled = uiModel.canMerge,
                onClick = { isMergeMenuOpen = true },
            )

            if (isMergeMenuOpen) {
                MergeMenu(uiModel) { isMergeMenuOpen = false }
            }
        }

        ToggleableIconAction(
            key = AllIconsKeys.Actions.Collapseall,
            description = "Fold repeated lines into one row",
            value = uiModel.collapseDuplicates,
            onValueChange = { uiModel.eventHandler(OnCollapseDuplicatesToggled) },
        )

        ToggleableIconAction(
            key = AllIconsKeys.RunConfigurations.Scroll_down,
            description = "Follow new lines",
            value = uiModel.followTail,
            onValueChange = { uiModel.eventHandler(OnFollowTailToggled) },
        )

        IconAction(
            key = AllIconsKeys.General.Close,
            description = "Close this pane",
            enabled = uiModel.canClose,
            onClick = { uiModel.eventHandler(OnCloseClicked) },
        )
    }
}

@Composable
private fun SplitMenu(uiModel: LogPaneUiModel, onDismissRequest: () -> Unit) {
    PopupContainer(
        onDismissRequest = onDismissRequest,
        horizontalAlignment = Alignment.Start,
        modifier = Modifier.width(MERGE_MENU_WIDTH),
    ) {
        Column(Modifier.padding(vertical = 3.dp)) {
            SPLIT_DIRECTIONS.forEach { (orientation, side) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(MERGE_ROW_HEIGHT)
                        .clickable {
                            uiModel.eventHandler(OnSplitPaneRequested(orientation, side))
                            onDismissRequest()
                        }
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        key = if (orientation == LayoutOrientation.Horizontal) {
                            AllIconsKeys.Actions.SplitVertically
                        } else {
                            AllIconsKeys.Actions.SplitHorizontally
                        },
                        contentDescription = null,
                    )
                    Text(splitLabel(orientation, side), style = JewelTheme.typography.regular, maxLines = 1)
                }
            }

            Text(
                text = "Right-click a tag to carry it into the new pane instead.",
                style = JewelTheme.typography.small,
                color = JewelTheme.globalColors.text.info,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}

@Composable
private fun MergeMenu(uiModel: LogPaneUiModel, onDismissRequest: () -> Unit) {
    PopupContainer(
        onDismissRequest = onDismissRequest,
        horizontalAlignment = Alignment.Start,
        modifier = Modifier.width(MERGE_MENU_WIDTH),
    ) {
        Column(Modifier.padding(vertical = 3.dp)) {
            Text(
                text = "Fold this pane into",
                style = JewelTheme.typography.small,
                color = JewelTheme.globalColors.text.info,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            )

            uiModel.otherPanes.forEach { target ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(MERGE_ROW_HEIGHT)
                        .clickable {
                            uiModel.eventHandler(OnMergeRequested(target.id))
                            onDismissRequest()
                        }
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = target.label,
                        style = JewelTheme.typography.regular,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Text(
                text = "That pane keeps its own search and level, and gains this pane's tags.",
                style = JewelTheme.typography.small,
                color = JewelTheme.globalColors.text.info,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}

@Composable
private fun SearchRow(uiModel: LogPaneUiModel) {
    val searchState = rememberTextFieldState(uiModel.queryInput)

    LaunchedEffect(searchState) {
        snapshotFlow { searchState.text.toString() }.collect { uiModel.eventHandler(OnQueryChanged(it)) }
    }

    // Clearing the filter resets the presenter's query; the editor buffer has to follow it back.
    LaunchedEffect(uiModel.queryInput) {
        if (searchState.text.toString() != uiModel.queryInput) {
            searchState.setTextAndPlaceCursorAtEnd(uiModel.queryInput)
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 3.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        TextField(
            state = searchState,
            modifier = Modifier.weight(1f),
            placeholder = { Text(uiModel.searchPlaceholder, style = JewelTheme.typography.small) },
            leadingIcon = { Icon(AllIconsKeys.Actions.Search, contentDescription = null) },
        )

        ToggleableIconAction(
            key = AllIconsKeys.Actions.MatchCase,
            description = "Match case",
            value = uiModel.filter.matchCase,
            onValueChange = { uiModel.eventHandler(OnMatchCaseToggled) },
        )

        ToggleableIconAction(
            key = AllIconsKeys.Actions.Regex,
            description = "Regular expression",
            value = uiModel.filter.useRegex,
            onValueChange = { uiModel.eventHandler(OnRegexToggled) },
        )
    }
}

@Composable
private fun SelectedTagRow(uiModel: LogPaneUiModel, tagGrip: @Composable (tag: String) -> Modifier) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tagPairing = rememberTagClickPairing()
        val tagActions = TagActions(
            canExtractTag = uiModel.canSplitTags,
            onClick = { tag -> if (tagPairing.isDoubleClick(tag)) uiModel.eventHandler(OnTagToggled(tag)) },
            onExtractTag = { tag, orientation, side ->
                uiModel.eventHandler(OnTagSplitRequested(tag, orientation, side))
            },
            onSplitPane = { orientation, side ->
                uiModel.eventHandler(OnSplitPaneRequested(orientation, side))
            },
        )

        uiModel.filter.tags.take(MAX_VISIBLE_CHIPS).forEach { tag ->
            Row(
                modifier = tagGrip(tag)
                    .clip(RoundedCornerShape(3.dp))
                    .background(JewelTheme.globalColors.outlines.focused.copy(alpha = 0.20f))
                    .padding(start = 4.dp, end = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                // The label is not a button. Only the cross removes the tag, so reading the chip
                // cannot lose it.
                TagTarget(tag, tagActions) {
                    Text(
                        text = tag.trim(),
                        style = JewelTheme.typography.small,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = CHIP_MAX_TEXT_WIDTH),
                    )
                }

                Tooltip(tooltip = { Text("Remove $tag") }) {
                    Icon(
                        key = AllIconsKeys.General.CloseSmall,
                        contentDescription = "Remove $tag",
                        modifier = Modifier
                            .pointerHoverIcon(TAG_CURSOR)
                            .clickable { uiModel.eventHandler(OnTagToggled(tag)) },
                    )
                }
            }
        }

        if (uiModel.filter.tags.size > MAX_VISIBLE_CHIPS) {
            Text(
                text = "+${uiModel.filter.tags.size - MAX_VISIBLE_CHIPS}",
                style = JewelTheme.typography.small,
                color = JewelTheme.globalColors.text.info,
            )
        }
    }
}

@Composable
private fun LogLines(uiModel: LogPaneUiModel, modifier: Modifier) {
    // Resolved once for the whole list rather than per row, which is also where the zoom applies.
    val console = JewelTheme.typography.consoleTextStyle.scaledBy(uiModel.fontScale)
    val listState = rememberLazyListState()
    val horizontalScroll = rememberScrollState()
    val density = LocalDensity.current
    val lineCount = uiModel.matchCount

    // A session event has nothing to do with any pane's filter, so it is placed among these rows by
    // sequence number rather than being indexed and matched the way a line is.
    val placements = remember(uiModel.snapshot, uiModel.events) { placementsFor(uiModel.snapshot, uiModel.events) }
    val totalCount = lineCount + placements.size

    // Wrapped lines already fit the pane, so sideways scrolling only applies when they do not.
    val scrollsSideways = !uiModel.columns.softWrap

    val tagPairing = rememberTagClickPairing()
    val tagActions = TagActions(
        canExtractTag = uiModel.canSplitTags,
        onClick = { tag -> if (tagPairing.isDoubleClick(tag)) uiModel.eventHandler(OnTagToggled(tag)) },
        onExtractTag = { tag, orientation, side ->
            uiModel.eventHandler(OnTagSplitRequested(tag, orientation, side))
        },
        onSplitPane = { orientation, side ->
            uiModel.eventHandler(OnSplitPaneRequested(orientation, side))
        },
    )
    var viewportWidth by remember { mutableStateOf(0.dp) }

    LaunchedEffect(uiModel.revision, uiModel.followTail, totalCount) {
        if (uiModel.followTail && totalCount > 0) listState.scrollToItem(totalCount - 1)
    }

    val renderLine: @Composable (Int) -> Unit = { index ->
        LogRow(
            line = uiModel.snapshot[index],
            previous = if (index > 0) uiModel.snapshot[index - 1] else null,
            repeatCount = uiModel.snapshot.repeatCountAt(index),
            columns = uiModel.columns,
            console = console,
            minWidth = if (scrollsSideways) viewportWidth else Dp.Unspecified,
            tagActions = tagActions,
            selectMessageOnly = uiModel.selectMessageOnly,
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { viewportWidth = with(density) { it.width.toDp() } }
            .onPointerEvent(PointerEventType.Scroll) { event ->
                var deltaX = 0f
                var deltaY = 0f
                event.changes.forEach {
                    deltaX += it.scrollDelta.x
                    deltaY += it.scrollDelta.y
                }
                if (isDeliberateUpwardScroll(deltaX, deltaY)) uiModel.eventHandler(OnScrolledUp)
            },
    ) {
        // Scoped by the message-only setting inside each row layout (see MaybeDisableSelection) so a
        // drag across rows copies exactly the log content the setting says it should.
        SelectionContainer {
        Box(
            Modifier
                .fillMaxSize()
                .then(if (scrollsSideways) Modifier.horizontalScroll(horizontalScroll) else Modifier),
        ) {
            LazyColumn(
                state = listState,
                modifier = if (scrollsSideways) Modifier.fillMaxHeight() else Modifier.fillMaxSize(),
            ) {
                var from = 0
                placements.forEach { (insertBeforeIndex, event) ->
                    if (insertBeforeIndex > from) {
                        val segmentStart = from
                        items(insertBeforeIndex - segmentStart, key = { uiModel.snapshot.seqAt(segmentStart + it) }) {
                            renderLine(segmentStart + it)
                        }
                    }
                    item(key = "event-${event.seq}") {
                        SessionEventRow(
                            event = event,
                            minWidth = if (scrollsSideways) viewportWidth else Dp.Unspecified,
                            selectMessageOnly = uiModel.selectMessageOnly,
                        )
                    }
                    from = insertBeforeIndex
                }
                if (from < lineCount) {
                    val segmentStart = from
                    items(lineCount - segmentStart, key = { uiModel.snapshot.seqAt(segmentStart + it) }) {
                        renderLine(segmentStart + it)
                    }
                }
            }
        }
        }

        VerticalScrollbar(
            scrollState = listState,
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
        )

        if (scrollsSideways) {
            HorizontalScrollbar(
                scrollState = horizontalScroll,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            )
        }

        if (totalCount == 0) {
            Text(
                text = if (uiModel.hasActiveFilter) "No lines match this filter" else "Waiting for lines",
                style = JewelTheme.typography.small,
                color = JewelTheme.globalColors.text.info,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

/**
 * Where each session event lands among a pane's own matching lines, found by its sequence number
 * rather than by filtering: an event is not a line, so it never competes with a pane's own filter.
 *
 * A marker only earns its place when this pane actually has a matching line after it and before the
 * next marker (or the end) — otherwise it is dead weight in a narrowly filtered pane, where long
 * stretches between matches would otherwise read as a wall of connection history and nothing else.
 */
private fun placementsFor(snapshot: LogSnapshot, events: List<SessionEvent>): List<Pair<Int, SessionEvent>> {
    if (events.isEmpty()) return emptyList()
    val placed = events.map { event ->
        var low = 0
        var high = snapshot.size
        while (low < high) {
            val mid = (low + high) / 2
            if (snapshot.seqAt(mid) < event.seq) low = mid + 1 else high = mid
        }
        low to event
    }
    return placed.filterIndexed { i, (insertBeforeIndex, _) ->
        val nextBoundary = placed.getOrNull(i + 1)?.first ?: snapshot.size
        nextBoundary > insertBeforeIndex
    }
}

@Composable
private fun SessionEventRow(event: SessionEvent, minWidth: Dp, selectMessageOnly: Boolean) {
    val scrollsSideways = minWidth != Dp.Unspecified

    Row(
        modifier = Modifier
            // A bare fillMaxWidth() collapses to content size inside the horizontal-scroll container
            // every other row already routes around the same way, via a viewport-wide floor instead.
            .then(if (scrollsSideways) Modifier.widthIn(min = minWidth) else Modifier.fillMaxWidth())
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Divider(Orientation.Horizontal, modifier = Modifier.weight(1f))
        MaybeDisableSelection(selectMessageOnly) {
            Text(
                text = "${event.kind.label} · ${formatTimeOfDay(event.timestampMillis)}",
                style = JewelTheme.typography.regular,
                fontWeight = FontWeight.Medium,
                color = JewelTheme.globalColors.text.info,
                maxLines = 1,
                softWrap = false,
            )
        }
        Divider(Orientation.Horizontal, modifier = Modifier.weight(1f))
    }
}

/** Only excludes its content from selection when the setting narrows selection to message text. */
@Composable
private fun MaybeDisableSelection(disabled: Boolean, content: @Composable () -> Unit) {
    if (disabled) DisableSelection(content) else content()
}

@Composable
private fun LogRow(
    line: LogLine,
    previous: LogLine?,
    repeatCount: Int,
    columns: LogColumns,
    console: TextStyle,
    minWidth: Dp,
    tagActions: TagActions,
    selectMessageOnly: Boolean,
) {
    when (columns.layout) {
        LogLineLayout.Columns ->
            ColumnarLogRow(line, repeatCount, columns, console, minWidth, tagActions, selectMessageOnly)
        LogLineLayout.Stacked ->
            StackedLogRow(line, previous, repeatCount, columns, console, minWidth, tagActions, selectMessageOnly)
    }
}

/** Everything the tag on a log line can do, so each row layout does not grow its own parameters. */
private class TagActions(
    /** True when the pane has a tag to spare, so the split can carry that tag out with it. */
    val canExtractTag: Boolean,
    val onClick: (String) -> Unit,
    val onExtractTag: (String, LayoutOrientation, SplitSide) -> Unit,
    val onSplitPane: (LayoutOrientation, SplitSide) -> Unit,
)

/**
 * The tag on a log line is a control: two clicks toggle it in this pane's filter, and right-click
 * pulls it out into a pane of its own. Pairing the clicks is [TagClickPairing]'s job, not this node's.
 */
@Composable
private fun TagTarget(tag: String, actions: TagActions, content: @Composable () -> Unit) {
    // Always available. A pane watching several tags splits by carrying the clicked tag out; a pane
    // pinned to one splits into a fresh pane beside it, since removing its only tag would just leave
    // it showing everything.
    ContextMenuArea(
        items = {
            SPLIT_DIRECTIONS.map { (orientation, side) ->
                ContextMenuItemOption(
                    icon = if (orientation == LayoutOrientation.Horizontal) {
                        AllIconsKeys.Actions.SplitVertically
                    } else {
                        AllIconsKeys.Actions.SplitHorizontally
                    },
                    label = if (actions.canExtractTag) {
                        "${splitLabel(orientation, side)} with \"${tag.trim()}\""
                    } else {
                        splitLabel(orientation, side)
                    },
                    action = {
                        if (actions.canExtractTag) {
                            actions.onExtractTag(tag, orientation, side)
                        } else {
                            actions.onSplitPane(orientation, side)
                        }
                    },
                )
            }
        },
    ) {
        Box(
            // Bound to the primary button so it cannot swallow the right-click the menu needs.
            Modifier
                .pointerHoverIcon(TAG_CURSOR)
                .onClick(matcher = PointerMatcher.Primary) { actions.onClick(tag) },
        ) {
            content()
        }
    }
}

private val SPLIT_DIRECTIONS = listOf(
    LayoutOrientation.Horizontal to SplitSide.Before,
    LayoutOrientation.Horizontal to SplitSide.After,
    LayoutOrientation.Vertical to SplitSide.Before,
    LayoutOrientation.Vertical to SplitSide.After,
)

private fun splitLabel(orientation: LayoutOrientation, side: SplitSide): String =
    when {
        orientation == LayoutOrientation.Horizontal && side == SplitSide.Before -> "Split left"
        orientation == LayoutOrientation.Horizontal -> "Split right"
        side == SplitSide.Before -> "Split up"
        else -> "Split down"
    }

/** Marks a row that stands for several identical lines. */
@Composable
private fun RepeatBadge(count: Int, console: TextStyle) {
    Text(
        text = "×$count",
        style = console,
        fontSize = console.fontSize * METADATA_TEXT_SCALE,
        fontWeight = FontWeight.Bold,
        color = JewelTheme.globalColors.text.normal,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(JewelTheme.globalColors.outlines.focused.copy(alpha = 0.28f))
            .padding(horizontal = 3.dp),
    )
}

@Composable
private fun StackedLogRow(
    line: LogLine,
    previous: LogLine?,
    repeatCount: Int,
    columns: LogColumns,
    console: TextStyle,
    minWidth: Dp,
    tagActions: TagActions,
    selectMessageOnly: Boolean,
) {
    val levelColor = colorFor(line.level)
    val dimColor = JewelTheme.globalColors.text.info
    val scrollsSideways = minWidth != Dp.Unspecified
    val metadata = stackedMetadataParts(line, columns)

    Column(
        modifier = Modifier
            .then(if (scrollsSideways) Modifier.widthIn(min = minWidth) else Modifier.fillMaxWidth())
            .padding(horizontal = 4.dp),
    ) {
        if (metadata.isNotEmpty() && needsMetadataHeader(previous, line)) {
            MaybeDisableSelection(selectMessageOnly) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    metadata.forEachIndexed { index, part ->
                        // Small text needs the contrast the message does not: the values are read at
                        // a glance, while the separators are only structure and stay quiet.
                        if (index > 0) MetadataText(METADATA_SEPARATOR, dimColor, console)

                        if (part.isTag) {
                            TagTarget(line.tag, tagActions) {
                                MetadataText(part.text, JewelTheme.globalColors.text.normal, console)
                            }
                        } else {
                            MetadataText(part.text, JewelTheme.globalColors.text.normal, console)
                        }
                    }
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Message-only selection is a setting, not a fixed rule — see MaybeDisableSelection.
            if (repeatCount > 1) MaybeDisableSelection(selectMessageOnly) { RepeatBadge(repeatCount, console) }

            Text(
                text = line.message,
                style = console,
                color = levelColor,
                // softWrap only governs whether a long physical line wraps at the pane's edge or
                // scrolls sideways; a message joined by LogcatParser (a stack trace, most often)
                // carries its own embedded '\n's that must always break, so maxLines stays uncapped.
                softWrap = columns.softWrap,
                maxLines = Int.MAX_VALUE,
                overflow = TextOverflow.Clip,
            )
        }
    }
}

@Composable
private fun MetadataText(
    text: String,
    color: androidx.compose.ui.graphics.Color,
    console: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = console,
        fontSize = console.fontSize * METADATA_TEXT_SCALE,
        fontWeight = FontWeight.Medium,
        color = color,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

private const val METADATA_SEPARATOR = "  ·  "

@Composable
private fun ColumnarLogRow(
    line: LogLine,
    repeatCount: Int,
    columns: LogColumns,
    console: TextStyle,
    minWidth: Dp,
    tagActions: TagActions,
    selectMessageOnly: Boolean,
) {
    val levelColor = colorFor(line.level)
    val dimColor = JewelTheme.globalColors.text.info
    val scrollsSideways = minWidth != Dp.Unspecified

    Row(
        modifier = Modifier
            // Sizing to the content is what gives the pane something to scroll to, but a row must
            // still fill the pane when the output is short.
            .then(if (scrollsSideways) Modifier.widthIn(min = minWidth) else Modifier.fillMaxWidth())
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Message-only selection is a setting, not a fixed rule — see MaybeDisableSelection.
        MaybeDisableSelection(selectMessageOnly) {
            if (columns.timestamp) {
                Text(
                    text = formatTimeOfDay(line.timestampMillis),
                    style = console,
                    color = dimColor,
                    maxLines = 1,
                    softWrap = false,
                )
            }

            if (columns.processIds) {
                Text(
                    text = "${line.pid}-${line.tid}",
                    style = console,
                    color = dimColor,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.width(PROCESS_COLUMN_WIDTH),
                )
            }

            if (columns.level) {
                Text(
                    text = line.level.letter.toString(),
                    style = console,
                    color = levelColor,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                )
            }

            if (columns.tag) {
                TagTarget(line.tag, tagActions) {
                    Text(
                        text = line.tag.trim(),
                        style = console,
                        color = dimColor,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(TAG_COLUMN_WIDTH),
                    )
                }
            }

            if (repeatCount > 1) RepeatBadge(repeatCount, console)
        }

        Text(
            text = line.message,
            style = console,
            color = levelColor,
            // softWrap only governs whether a long physical line wraps at the pane's edge or
            // scrolls sideways; a message joined by LogcatParser (a stack trace, most often)
            // carries its own embedded '\n's that must always break, so maxLines stays uncapped.
            softWrap = columns.softWrap,
            maxLines = Int.MAX_VALUE,
            overflow = TextOverflow.Clip,
            modifier = if (columns.softWrap) Modifier.weight(1f) else Modifier,
        )
    }
}

@Composable
private fun PaneStatusBar(uiModel: LogPaneUiModel) {
    Row(
        modifier = Modifier.fillMaxWidth().height(STATUS_HEIGHT).padding(horizontal = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "${formatCount(uiModel.matchCount.toLong())} lines",
            style = JewelTheme.typography.small,
            color = JewelTheme.globalColors.text.info,
            maxLines = 1,
        )

        if (uiModel.hasActiveFilter) {
            Text(
                text = uiModel.filterSummary,
                style = JewelTheme.typography.small,
                color = JewelTheme.globalColors.text.info,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }

        if (uiModel.isRebuilding) {
            Text(
                text = "refiltering",
                style = JewelTheme.typography.small,
                color = JewelTheme.globalColors.text.info,
                maxLines = 1,
            )
        }

        if (uiModel.hasInvalidRegex) {
            Text(
                text = "invalid pattern",
                style = JewelTheme.typography.small,
                color = colorFor(LogLevel.WARN),
                maxLines = 1,
            )
        }
    }
}

private val TOOLBAR_HEIGHT = 26.dp
private val STATUS_HEIGHT = 18.dp
private val LEVEL_SELECTOR_WIDTH = 100.dp
private val TAG_COLUMN_WIDTH = 96.dp
private val PROCESS_COLUMN_WIDTH = 68.dp
private const val METADATA_TEXT_SCALE = 0.82f
private val MOVE_CURSOR = PointerIcon(Cursor(Cursor.MOVE_CURSOR))
private val TAG_CURSOR = PointerIcon(Cursor(Cursor.HAND_CURSOR))
private val CHIP_MAX_TEXT_WIDTH = 120.dp
private val MERGE_MENU_WIDTH = 240.dp
private val MERGE_ROW_HEIGHT = 24.dp
private const val MAX_VISIBLE_CHIPS = 3

/** Line height is left to follow the size, so it is not scaled twice. */
private fun TextStyle.scaledBy(scale: Float): TextStyle =
    if (scale == 1f) this else copy(fontSize = fontSize * scale)
