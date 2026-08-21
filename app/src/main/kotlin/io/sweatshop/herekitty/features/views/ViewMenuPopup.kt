package io.sweatshop.herekitty.features.views

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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.sweatshop.herekitty.domain.features.views.model.ViewConfig
import io.sweatshop.herekitty.ui.component.IconAction
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Link
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.PopupContainer
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.ui.typography

/**
 * Saved views come first: picking one is the common action, naming a new one is the occasional one.
 */
@Composable
fun ViewMenuPopup(
    current: ViewConfig,
    actions: ViewActions,
    onDismissRequest: () -> Unit,
) {
    val newNameState = rememberTextFieldState()

    PopupContainer(
        onDismissRequest = onDismissRequest,
        horizontalAlignment = Alignment.Start,
        modifier = Modifier.width(POPUP_WIDTH),
    ) {
        Column(Modifier.padding(vertical = 4.dp)) {
            if (actions.savedViews.isEmpty()) {
                Text(
                    text = "No saved views yet. Name this pane setup below to keep it.",
                    style = JewelTheme.typography.small,
                    color = JewelTheme.globalColors.text.info,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            } else {
                VerticallyScrollableContainer(
                    scrollState = rememberLazyListState(),
                    modifier = Modifier.heightIn(max = LIST_MAX_HEIGHT),
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        actions.savedViews.forEach { saved ->
                            SavedViewRow(
                                view = saved,
                                isCurrent = saved.name.isNotBlank() && saved.name == current.name,
                                // Nothing to write when it already says what this pane setup says.
                                canOverwrite = !current.hasSameSetupAs(saved),
                                onOverwrite = {
                                    actions.onSave(current.copy(name = saved.name))
                                    onDismissRequest()
                                },
                                onApply = {
                                    actions.onApply(saved)
                                    onDismissRequest()
                                },
                                onExport = { actions.onExport(saved) },
                                onDelete = { actions.onDelete(saved.name) },
                            )
                        }
                    }
                }
            }

            Divider(Orientation.Horizontal, modifier = Modifier.padding(vertical = 4.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val newName = newNameState.text.toString().trim()

                TextField(
                    state = newNameState,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Save as a new view…", style = JewelTheme.typography.small) },
                )
                OutlinedButton(
                    enabled = newName.isNotEmpty(),
                    onClick = {
                        actions.onSave(current.copy(name = newName))
                        onDismissRequest()
                    },
                ) {
                    Text("Save as")
                }
            }

            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Link(text = "Export…", onClick = { actions.onExport(current) })
                Link(
                    text = "Import…",
                    onClick = {
                        actions.onImport()
                        onDismissRequest()
                    },
                )
            }
        }
    }
}

@Composable
private fun SavedViewRow(
    view: ViewConfig,
    isCurrent: Boolean,
    canOverwrite: Boolean,
    onOverwrite: () -> Unit,
    onApply: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT)
            .background(
                if (isCurrent) {
                    JewelTheme.globalColors.outlines.focused.copy(alpha = 0.18f)
                } else {
                    JewelTheme.globalColors.panelBackground
                },
            )
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Column(Modifier.weight(1f).clickable(onClick = onApply)) {
            Text(
                text = view.name,
                style = JewelTheme.typography.regular,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = view.summary,
                style = JewelTheme.typography.small,
                color = JewelTheme.globalColors.text.info,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Only the view actually in use can be saved back to; writing this setup over some other
        // saved view is not a thing anyone means to do.
        if (isCurrent) {
            IconAction(
                key = AllIconsKeys.Actions.MenuSaveall,
                description = if (canOverwrite) {
                    "Save changes to \"${view.name}\""
                } else {
                    "\"${view.name}\" is up to date"
                },
                enabled = canOverwrite,
                onClick = onOverwrite,
            )
        }
        IconAction(
            key = AllIconsKeys.Actions.Upload,
            description = "Export \"${view.name}\"",
            onClick = onExport,
        )
        IconAction(
            key = AllIconsKeys.General.Delete,
            description = "Delete \"${view.name}\"",
            onClick = onDelete,
        )
    }
}

private val POPUP_WIDTH = 340.dp
private val LIST_MAX_HEIGHT = 260.dp
private val ROW_HEIGHT = 36.dp
