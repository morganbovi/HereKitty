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
import io.sweatshop.herekitty.features.session.SessionUiModel
import io.sweatshop.herekitty.features.session.SessionUiModel.Event.OnCloseCancelled
import io.sweatshop.herekitty.features.session.SessionUiModel.Event.OnCloseConfirmed
import io.sweatshop.herekitty.ui.dialog.HereKittyDialog
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.typography


/**
 * Asked before closing a session whose named view has unsaved changes. An unnamed setup never gets
 * here, so this is always a save-or-discard question, never a naming one.
 */
@Composable
fun CloseSessionPrompt(uiModel: SessionUiModel) {
    HereKittyDialog(
        title = "Close ${uiModel.source.label}",
        size = DIALOG_SIZE,
        onCloseRequest = { uiModel.eventHandler(OnCloseCancelled) },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "\"${uiModel.view.name}\" has changes that are not saved.",
                style = JewelTheme.typography.regular,
            )

            Text(
                text = uiModel.view.summary,
                style = JewelTheme.typography.small,
                color = JewelTheme.globalColors.text.info,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
            ) {
                OutlinedButton(onClick = { uiModel.eventHandler(OnCloseCancelled) }) {
                    Text("Cancel")
                }
                OutlinedButton(onClick = { uiModel.eventHandler(OnCloseConfirmed(saveAs = null)) }) {
                    Text("Discard changes")
                }
                DefaultButton(
                    onClick = { uiModel.eventHandler(OnCloseConfirmed(saveAs = uiModel.view.name)) },
                ) {
                    Text("Save and close")
                }
            }

            CheckboxRow(
                modifier = Modifier.align(Alignment.End),
                text = "Don't ask again",
                checked = !uiModel.askBeforeClosing,
                onCheckedChange = { uiModel.eventHandler(SessionUiModel.Event.OnAskBeforeClosingChanged(!it)) },
            )
        }
    }
}

private val DIALOG_SIZE = DpSize(420.dp, 170.dp)
