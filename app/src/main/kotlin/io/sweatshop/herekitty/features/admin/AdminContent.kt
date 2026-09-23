package io.sweatshop.herekitty.features.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import io.sweatshop.herekitty.domain.features.logs.model.LogLevel
import io.sweatshop.herekitty.ui.theme.colorFor
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Checkbox
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.typography

@Composable
fun AdminContent(uiModel: AdminUiModel, modifier: Modifier = Modifier) {
    val emailState = rememberTextFieldState()
    val email = emailState.text.toString().trim()

    Box(modifier.fillMaxSize().background(JewelTheme.globalColors.panelBackground)) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .widthIn(max = 420.dp)
                .shadow(elevation = 3.dp, shape = RoundedCornerShape(10.dp))
                .clip(RoundedCornerShape(10.dp))
                .background(JewelTheme.globalColors.panelBackground)
                .background(JewelTheme.globalColors.outlines.focused.copy(alpha = 0.05f))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Add a user to the org", style = JewelTheme.typography.h3TextStyle)

            TextField(
                state = emailState,
                placeholder = { Text("email address") },
                modifier = Modifier.fillMaxWidth(),
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = uiModel.roleIsAdmin,
                    onCheckedChange = { uiModel.eventHandler(AdminUiModel.Event.OnRoleIsAdminChanged(it)) },
                )
                Text("Grant admin")
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DefaultButton(
                    enabled = !uiModel.isSubmitting && email.isNotEmpty(),
                    onClick = { uiModel.eventHandler(AdminUiModel.Event.OnSubmitClicked(email)) },
                ) {
                    Text(if (uiModel.isSubmitting) "Adding…" else "Add to org")
                }
                OutlinedButton(onClick = { uiModel.eventHandler(AdminUiModel.Event.OnCloseClicked) }) {
                    Text("Close")
                }
            }

            uiModel.resultMessage?.let { message ->
                Text(message, style = JewelTheme.typography.small)
            }
            uiModel.error?.let { message ->
                Text(message, style = JewelTheme.typography.small, color = colorFor(LogLevel.ERROR))
            }
        }
    }
}
