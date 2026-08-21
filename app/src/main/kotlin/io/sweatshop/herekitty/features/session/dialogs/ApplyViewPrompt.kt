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
import io.sweatshop.herekitty.domain.features.views.model.ViewConfig
import io.sweatshop.herekitty.features.session.SessionUiModel
import io.sweatshop.herekitty.features.session.SessionUiModel.Event.OnViewApplyCancelled
import io.sweatshop.herekitty.features.session.SessionUiModel.Event.OnViewApplyConfirmed
import io.sweatshop.herekitty.ui.dialog.HereKittyDialog
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.typography

/**
 * Asked before a saved view replaces an arrangement that was never saved.
 *
 * It deliberately offers no save button: cancelling leaves the setup untouched, and the view menu is
 * already where saving lives. A second way to save it would be a second place to keep working.
 */
@Composable
fun ApplyViewPrompt(uiModel: SessionUiModel, incoming: ViewConfig) {
    HereKittyDialog(
        title = "Open \"${incoming.name}\"?",
        size = DIALOG_SIZE,
        onCloseRequest = { uiModel.eventHandler(OnViewApplyCancelled) },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "This replaces the ${uiModel.panes.size} panes you have open, which are not saved.",
                style = JewelTheme.typography.regular,
            )

            Text(
                text = uiModel.view.summary,
                style = JewelTheme.typography.small,
                color = JewelTheme.globalColors.text.info,
            )

            Text(
                text = "Cancel if you want to save it from the view menu first.",
                style = JewelTheme.typography.small,
                color = JewelTheme.globalColors.text.info,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
            ) {
                OutlinedButton(onClick = { uiModel.eventHandler(OnViewApplyCancelled) }) {
                    Text("Cancel")
                }
                DefaultButton(onClick = { uiModel.eventHandler(OnViewApplyConfirmed) }) {
                    Text("Open \"${incoming.name}\"")
                }
            }
        }
    }
}

private val DIALOG_SIZE = DpSize(440.dp, 195.dp)
