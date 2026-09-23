package io.sweatshop.herekitty.features.relay

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Link
import org.jetbrains.jewel.ui.component.PopupContainer
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.typography

@Composable
fun RelaySettingsPopup(
    signedInAs: String,
    onSignOutClicked: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    PopupContainer(
        onDismissRequest = onDismissRequest,
        horizontalAlignment = Alignment.Start,
        modifier = Modifier.width(POPUP_WIDTH),
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                text = "Signed in as $signedInAs",
                style = JewelTheme.typography.small,
                color = JewelTheme.globalColors.text.info,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Link(
                text = "Sign out",
                onClick = {
                    onSignOutClicked()
                    onDismissRequest()
                },
            )
        }
    }
}

private val POPUP_WIDTH = 200.dp
