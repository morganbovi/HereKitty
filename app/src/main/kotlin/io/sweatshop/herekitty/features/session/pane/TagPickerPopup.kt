package io.sweatshop.herekitty.features.session.pane

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.sweatshop.herekitty.domain.features.logs.model.TagStats
import io.sweatshop.herekitty.ui.format.formatCount
import io.sweatshop.herekitty.ui.theme.colorFor
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Checkbox
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.PopupContainer
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.ui.typography

/**
 * The running list of tags this session has seen, searchable. Tags stay listed even after their
 * lines are evicted, so a tag seen once early on can still be selected later.
 */
@Composable
fun TagPickerPopup(
    tags: List<TagStats>,
    selectedTags: Set<String>,
    onTagToggled: (String) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val searchState = rememberTextFieldState()
    val query = searchState.text.toString()
    val searchFocus = remember { FocusRequester() }

    // The popup has to take window focus before it can hand it to a field, which takes a frame.
    LaunchedEffect(Unit) {
        withFrameNanos { }
        searchFocus.requestFocus()
    }

    val matches = tags.filter { query.isBlank() || it.tag.contains(query, ignoreCase = true) }

    PopupContainer(
        onDismissRequest = onDismissRequest,
        horizontalAlignment = Alignment.Start,
        modifier = Modifier.width(POPUP_WIDTH),
    ) {
        Column {
            TextField(
                state = searchState,
                modifier = Modifier.fillMaxWidth().padding(4.dp).focusRequester(searchFocus),
                placeholder = { Text("Search ${tags.size} tags", style = JewelTheme.typography.small) },
                leadingIcon = { Icon(AllIconsKeys.Actions.Search, contentDescription = null) },
            )

            Divider(Orientation.Horizontal)

            if (matches.isEmpty()) {
                Text(
                    text = if (tags.isEmpty()) "No tags seen yet" else "No tag matches \"$query\"",
                    style = JewelTheme.typography.small,
                    color = JewelTheme.globalColors.text.info,
                    modifier = Modifier.padding(8.dp),
                )
            } else {
                VerticallyScrollableContainer(
                    scrollState = rememberLazyListState(),
                    modifier = Modifier.heightIn(max = POPUP_MAX_LIST_HEIGHT),
                ) {
                    LazyColumn(Modifier.fillMaxWidth()) {
                        items(matches, key = { it.tag }) { stats ->
                            TagRow(
                                stats = stats,
                                isSelected = stats.tag in selectedTags,
                                onClick = { onTagToggled(stats.tag) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TagRow(stats: TagStats, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT)
            .background(if (isSelected) JewelTheme.globalColors.outlines.focused.copy(alpha = 0.18f) else JewelTheme.globalColors.panelBackground)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Checkbox(checked = isSelected, onCheckedChange = { onClick() })

        Text(
            text = stats.tag,
            style = JewelTheme.typography.regular,
            color = colorFor(stats.highestLevel),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
            modifier = Modifier.weight(1f),
        )

        Text(
            text = formatCount(stats.count),
            style = JewelTheme.typography.small,
            color = JewelTheme.globalColors.text.info,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

private val POPUP_WIDTH = 340.dp
private val POPUP_MAX_LIST_HEIGHT = 320.dp
private val ROW_HEIGHT = 22.dp
