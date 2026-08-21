package io.sweatshop.herekitty.app.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.sweatshop.herekitty.ui.dialog.HereKittyDialog
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.typography


@Composable
fun ExitPrompt(onCancel: () -> Unit, onExit: (stopAsking: Boolean) -> Unit) {
    var stopAsking by remember { mutableStateOf(false) }

    HereKittyDialog(title = "Quit HereKitty", size = DIALOG_SIZE, onCloseRequest = onCancel) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Quit HereKitty?", style = JewelTheme.typography.regular)

            Text(
                text = "Any recording you have not exported is lost. Saved views and the layout come back next time.",
                style = JewelTheme.typography.small,
                color = JewelTheme.globalColors.text.info,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
            ) {
                OutlinedButton(onClick = onCancel) { Text("Cancel") }
                DefaultButton(onClick = { onExit(stopAsking) }) { Text("Quit") }
            }

            CheckboxRow(
                modifier = Modifier.align(Alignment.End),
                text = "Don't ask again",
                checked = stopAsking,
                onCheckedChange = { stopAsking = it },
            )
        }
    }
}

private val DIALOG_SIZE = DpSize(420.dp, 170.dp)
