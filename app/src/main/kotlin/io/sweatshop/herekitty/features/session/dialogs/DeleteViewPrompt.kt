package io.sweatshop.herekitty.features.session.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.sweatshop.herekitty.ui.dialog.HereKittyDialog
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.typography

/**
 * Deleting a saved view removes the file behind it, and nothing here can put it back — so it asks
 * first, with no "don't ask again": the whole point is the one thing an accidental click cannot undo.
 */
@Composable
fun DeleteViewPrompt(name: String, isApplied: Boolean, onCancel: () -> Unit, onDelete: () -> Unit) {
    HereKittyDialog(title = "Delete \"$name\"", size = DIALOG_SIZE, onCloseRequest = onCancel) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Delete the view \"$name\"?", style = JewelTheme.typography.regular)

            Text(
                text = if (isApplied) {
                    "Its file is removed for good. The panes stay open as they are, but the setup " +
                        "becomes unsaved, so closing it will offer to save it again."
                } else {
                    "Its file is removed for good. Any session already using it keeps its panes."
                },
                style = JewelTheme.typography.small,
                color = JewelTheme.globalColors.text.info,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
            ) {
                OutlinedButton(onClick = onCancel) { Text("Cancel") }
                DefaultButton(onClick = onDelete) { Text("Delete") }
            }
        }
    }
}

private val DIALOG_SIZE = DpSize(430.dp, 185.dp)
