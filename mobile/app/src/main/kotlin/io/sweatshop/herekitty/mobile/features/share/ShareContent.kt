package io.sweatshop.herekitty.mobile.features.share

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareContent() {
    val presenter = koinInject<SharePresenter>()
    val uiModel = presenter.present()
    val context = LocalContext.current
    var settingsOpen by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("HereKitty", fontWeight = FontWeight.Bold) },
            actions = {
                IconButton(onClick = { settingsOpen = true }) {
                    androidx.compose.material3.Icon(Icons.Filled.Settings, contentDescription = "Settings")
                }
            },
        )
        if (settingsOpen) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { settingsOpen = false },
                title = { Text("Settings") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Manage permissions and connection access. Sharing stays active while the app is in the background.")
                        TextButton(onClick = {
                            context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                        }) { Text("Wireless debugging settings") }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = android.net.Uri.parse("package:${context.packageName}")
                        })
                    }) { Text("App settings") }
                },
                dismissButton = { TextButton(onClick = { settingsOpen = false }) { Text("Done") } },
            )
        }
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val device = uiModel.device
            if (device == null) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("Preparing this phone…", modifier = Modifier.padding(top = 16.dp))
            } else {
                DeviceName(uiModel)
                Text("Make this phone available to your HereKitty desktop app.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            if (device.isShared) "Sharing is on" else "Sharing is off",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        val connection = connectionPresentation(uiModel.bridgeStatus, device.isShared)
                        Text(connection.title, color = connection.color, fontWeight = FontWeight.Medium)
                        Text(connection.detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (connection.canRetry) {
                            OutlinedButton(
                                onClick = { uiModel.eventHandler(ShareUiModel.Event.OnRetryClicked) },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Retry connection") }
                        }
                        Button(
                            onClick = { uiModel.eventHandler(ShareUiModel.Event.OnSharedToggled(!device.isShared)) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(if (device.isShared) "Stop sharing" else "Start sharing") }
                    }
                }
            }
            uiModel.error?.let { message ->
                Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 16.dp))
                OutlinedButton(onClick = { uiModel.eventHandler(ShareUiModel.Event.OnErrorDismissed) }) { Text("Dismiss") }
            }
        }
    }
}

private data class ConnectionPresentation(
    val title: String,
    val detail: String,
    val color: androidx.compose.ui.graphics.Color,
    val canRetry: Boolean = false,
)

@Composable
private fun connectionPresentation(status: String, shared: Boolean): ConnectionPresentation {
    val colors = MaterialTheme.colorScheme
    val normalized = status.lowercase()
    return when {
        !shared -> ConnectionPresentation("Private", "Start sharing when you want a HereKitty desktop to connect.", colors.onSurfaceVariant)
        normalized.contains("connected") -> ConnectionPresentation("Connected", "A desktop is using this phone now.", colors.primary)
        normalized.contains("network changed") -> ConnectionPresentation("Reconnecting", "We are restoring access after this phone joined a network.", colors.tertiary, true)
        normalized.contains("reconnect") || normalized.contains("lost") -> ConnectionPresentation("Offline", "This phone is not available to desktops until its network connection returns.", colors.tertiary, true)
        normalized.contains("confirmation") || normalized.contains("wireless debugging") -> ConnectionPresentation("Needs your confirmation", "Open the Wireless debugging notification or Android settings, then retry.", colors.tertiary, true)
        normalized.contains("fail") || normalized.contains("error") -> ConnectionPresentation("Connection needs attention", "Check Wi-Fi and Wireless debugging, then try again.", colors.error, true)
        normalized.contains("connecting") || normalized.contains("finding") -> ConnectionPresentation("Connecting", "Preparing this phone for the requested desktop connection.", colors.primary)
        else -> ConnectionPresentation("Available", "This phone is online and ready for a HereKitty desktop.", colors.primary)
    }
}

@Composable
private fun DeviceName(uiModel: ShareUiModel) {
    val device = uiModel.device ?: return
    if (uiModel.editingName) {
        TextField(
            value = uiModel.nameDraft,
            onValueChange = { uiModel.eventHandler(ShareUiModel.Event.OnNameChanged(it)) },
            label = { Text("Phone name") },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.padding(top = 8.dp)) {
            Button(onClick = { uiModel.eventHandler(ShareUiModel.Event.OnNameSaved) }) { Text("Save") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = { uiModel.eventHandler(ShareUiModel.Event.OnNameEditCancelled) }) { Text("Cancel") }
        }
    } else {
        TextButton(onClick = { uiModel.eventHandler(ShareUiModel.Event.OnNameEditStarted) }) {
            Text(device.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("  Edit", color = MaterialTheme.colorScheme.primary)
        }
    }
}
