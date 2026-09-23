package io.sweatshop.herekitty.features.relay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.sweatshop.herekitty.domain.features.auth.AuthState
import io.sweatshop.herekitty.domain.features.logs.model.LogLevel
import io.sweatshop.herekitty.domain.features.relay.RelayBridgeStatus
import io.sweatshop.herekitty.domain.features.relay.RelayDevice
import io.sweatshop.herekitty.ui.component.IconAction
import io.sweatshop.herekitty.ui.theme.colorFor
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.ui.typography

@Composable
fun RelayDeviceGridContent(uiModel: RelayCardUiModel, modifier: Modifier = Modifier) {
    var isSettingsOpen by remember { mutableStateOf(false) }
    val signedInUser = uiModel.authState as? AuthState.Authenticated

    Box(modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (signedInUser != null) {
                Box {
                    IconAction(
                        key = AllIconsKeys.General.Settings,
                        description = "Relay settings",
                        onClick = { isSettingsOpen = true },
                    )
                    if (isSettingsOpen) {
                        RelaySettingsPopup(
                            signedInAs = signedInUser.displayName.ifBlank { signedInUser.email },
                            onSignOutClicked = { uiModel.eventHandler(RelayCardUiModel.Event.OnSignOutClicked) },
                            onDismissRequest = { isSettingsOpen = false },
                        )
                    }
                }
            }
            IconAction(
                key = AllIconsKeys.General.Close,
                description = "Back",
                onClick = { uiModel.eventHandler(RelayCardUiModel.Event.OnBrowseClosed) },
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.82f)
                .widthIn(max = 680.dp)
                .heightIn(min = 260.dp, max = 480.dp)
                .shadow(elevation = 3.dp, shape = RoundedCornerShape(10.dp))
                .clip(RoundedCornerShape(10.dp))
                .background(JewelTheme.globalColors.panelBackground)
                .background(JewelTheme.globalColors.outlines.focused.copy(alpha = 0.05f))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Relay", style = JewelTheme.typography.h3TextStyle, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { uiModel.eventHandler(RelayCardUiModel.Event.OnRefreshDevicesClicked) }) {
                    Text("Refresh")
                }
            }
            Text(
                text = "${uiModel.devices.count { it.isOnline }} device${if (uiModel.devices.count { it.isOnline } == 1) "" else "s"} online · shared by your organization",
                style = JewelTheme.typography.small,
                color = JewelTheme.globalColors.text.info,
            )

            val relayStatus = when (uiModel.relayReachable) {
                true -> "Relay service available"
                false -> "Relay service unavailable"
                null -> "Checking relay service…"
            }
            Text(
                text = relayStatus,
                style = JewelTheme.typography.small,
                color = if (uiModel.relayReachable == false) colorFor(LogLevel.ERROR) else JewelTheme.globalColors.text.info,
            )

            uiModel.wakeupStatus?.let {
                Text(it, style = JewelTheme.typography.small, color = JewelTheme.globalColors.text.info)
            }

            Box(Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(min = 150.dp, max = 270.dp)) {
                when {
                    uiModel.isLoadingDevices -> Text(
                        "Loading…",
                        style = JewelTheme.typography.small,
                        modifier = Modifier.align(Alignment.Center),
                    )

                    uiModel.devices.isEmpty() -> Text(
                        "No shared phones are available. Ask someone in your organization to start sharing their phone.",
                        style = JewelTheme.typography.small,
                        color = JewelTheme.globalColors.text.info,
                        modifier = Modifier.align(Alignment.Center),
                    )

                    else -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 220.dp),
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(uiModel.devices, key = { it.id }) { device ->
                            DeviceGridCard(
                                device = device,
                                status = uiModel.bridgeStatuses[device.id],
                                onConnectClicked = {
                                    if (device.isOnline) {
                                        uiModel.eventHandler(RelayCardUiModel.Event.OnConnectClicked(device.id))
                                    } else {
                                        uiModel.eventHandler(RelayCardUiModel.Event.OnRequestDeviceClicked(device.id))
                                    }
                                },
                                onDisconnectClicked = {
                                    uiModel.eventHandler(RelayCardUiModel.Event.OnDisconnectClicked(device.id))
                                },
                            )
                        }
                    }
                }
            }

        }
    }
}

@Composable
private fun DeviceGridCard(
    device: RelayDevice,
    status: RelayBridgeStatus?,
    onConnectClicked: () -> Unit,
    onDisconnectClicked: () -> Unit,
) {
    Column(
        modifier = Modifier
            .shadow(elevation = 1.dp, shape = RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .background(JewelTheme.globalColors.panelBackground)
            .background(JewelTheme.globalColors.outlines.focused.copy(alpha = 0.08f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(device.name, style = JewelTheme.typography.regular, fontWeight = FontWeight.Medium)
        Text(
            "Shared by ${device.ownerDisplayName}",
            style = JewelTheme.typography.small,
            color = JewelTheme.globalColors.text.info,
        )
        Text(
            if (device.isOnline) "Available" else "Phone is offline",
            style = JewelTheme.typography.small,
            color = if (device.isOnline) JewelTheme.globalColors.text.info else colorFor(LogLevel.WARN),
        )

        when (status) {
            null -> OutlinedButton(onClick = onConnectClicked) {
                Text(if (device.isOnline) "Connect" else "Request access")
            }

            RelayBridgeStatus.Claiming, RelayBridgeStatus.Bridging -> {
                Text("Connecting…", style = JewelTheme.typography.small, color = JewelTheme.globalColors.text.info)
                OutlinedButton(onClick = onDisconnectClicked) { Text("Cancel") }
            }

            is RelayBridgeStatus.Reconnecting -> {
                Text("Connection lost — retrying (${status.attempt}/3)…", style = JewelTheme.typography.small, color = colorFor(LogLevel.WARN))
                OutlinedButton(onClick = onDisconnectClicked) { Text("Stop retrying") }
            }

            is RelayBridgeStatus.Connected -> {
                Text(
                    if (status.transport == io.sweatshop.herekitty.domain.features.relay.RelayTransport.LocalNetwork) "Connected directly · Local network" else "Connected · Remote relay",
                    style = JewelTheme.typography.small,
                    color = JewelTheme.globalColors.text.info,
                )
                OutlinedButton(onClick = onDisconnectClicked) { Text("Disconnect") }
            }

            is RelayBridgeStatus.Failed -> {
                Text(status.message, style = JewelTheme.typography.small, color = colorFor(LogLevel.ERROR))
                OutlinedButton(onClick = onConnectClicked) { Text("Retry") }
            }
        }
    }
}
