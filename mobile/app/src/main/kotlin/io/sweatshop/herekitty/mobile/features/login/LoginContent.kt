package io.sweatshop.herekitty.mobile.features.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject

@Composable
fun LoginContent(onSignedIn: () -> Unit) {
    val presenter = koinInject<LoginPresenter>()
    val uiModel = presenter.present()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val userId = uiModel.signedInUserId
        if (userId == null) {
            Button(
                enabled = !uiModel.isLoading,
                onClick = { uiModel.eventHandler(LoginUiModel.Event.OnSignInClicked) },
                modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
            ) {
                if (uiModel.isLoading) CircularProgressIndicator()
                else Text("Continue with Google")
            }
        } else {
            onSignedIn()
        }
        uiModel.error?.let { message ->
            Text(message, style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { uiModel.eventHandler(LoginUiModel.Event.OnErrorDismissed) }) {
                Text("Dismiss")
            }
        }
    }
}
