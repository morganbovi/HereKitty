package io.sweatshop.herekitty.features.sourcepicker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.sweatshop.herekitty.domain.features.devices.model.AdbDevice
import io.sweatshop.herekitty.domain.features.logs.model.LogLevel
import io.sweatshop.herekitty.features.sourcepicker.SourcePickerUiModel.Event.OnCloseClicked
import io.sweatshop.herekitty.features.sourcepicker.SourcePickerUiModel.Event.OnDeviceClicked
import io.sweatshop.herekitty.features.sourcepicker.SourcePickerUiModel.Event.OnRecordingChosen
import io.sweatshop.herekitty.features.sourcepicker.SourcePickerUiModel.Event.OnInstallToolsClicked
import io.sweatshop.herekitty.features.sourcepicker.SourcePickerUiModel.Event.OnPendingViewCleared
import io.sweatshop.herekitty.features.sourcepicker.SourcePickerUiModel.Event.OnRetryClicked
import io.sweatshop.herekitty.ui.component.IconAction
import io.sweatshop.herekitty.ui.files.FileDialogs
import io.sweatshop.herekitty.ui.theme.colorFor
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.HorizontalProgressBar
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Link
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.ui.typography

@Composable
fun SourcePickerContent(uiModel: SourcePickerUiModel, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize()) {
        if (uiModel.canClose) {
            IconAction(
                key = AllIconsKeys.General.Close,
                description = "Close this split",
                onClick = { uiModel.eventHandler(OnCloseClicked) },
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
            )
        }

        Column(
            modifier = Modifier.align(Alignment.Center).widthIn(max = CARD_MAX_WIDTH).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Choose a source", style = JewelTheme.typography.h3TextStyle)

            uiModel.pendingViewLabel?.let { view ->
                Text(
                    text = "The view \"$view\" will be applied to it",
                    style = JewelTheme.typography.small,
                    color = JewelTheme.globalColors.text.info,
                    textAlign = TextAlign.Center,
                )
                Link(
                    text = "Start fresh instead",
                    onClick = { uiModel.eventHandler(OnPendingViewCleared) },
                )
            }

            when {
                uiModel.unavailableReason != null -> AdbUnavailable(uiModel)
                uiModel.devices.isEmpty() -> NoDevices(uiModel)
                else -> DeviceList(uiModel)
            }

            Divider(Orientation.Horizontal, modifier = Modifier.fillMaxWidth())

            OutlinedButton(
                onClick = {
                    FileDialogs.openRecording()?.let { uiModel.eventHandler(OnRecordingChosen(it)) }
                },
            ) {
                Text("Open a recording or bundle…")
            }

            Text(
                text = "A bundle brings its view with it. A bare recording opens as a session you can filter exactly like a live device.",
                style = JewelTheme.typography.small,
                color = JewelTheme.globalColors.text.info,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun DeviceList(uiModel: SourcePickerUiModel) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        uiModel.devices.forEach { device ->
            DeviceRow(
                device = device,
                isAlreadyOpen = device.serial in uiModel.busySerials,
                onClick = { uiModel.eventHandler(OnDeviceClicked(device)) },
            )
        }
    }
}

@Composable
private fun DeviceRow(device: AdbDevice, isAlreadyOpen: Boolean, onClick: () -> Unit) {
    val isSelectable = device.state.canStreamLogs

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT)
            .clip(RoundedCornerShape(4.dp))
            .background(JewelTheme.globalColors.panelBackground)
            .then(if (isSelectable) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            key = if (device.isEmulator) AllIconsKeys.General.Layout else AllIconsKeys.General.Mouse,
            contentDescription = null,
        )

        Column(Modifier.weight(1f)) {
            Text(
                text = device.displayName,
                style = JewelTheme.typography.regular,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
            )
            Text(
                text = device.serial,
                style = JewelTheme.typography.small,
                color = JewelTheme.globalColors.text.info,
                maxLines = 1,
                softWrap = false,
            )
        }

        Text(
            text = if (isAlreadyOpen) "${device.state.label} · open" else device.state.label,
            style = JewelTheme.typography.small,
            color = if (isSelectable) colorFor(LogLevel.INFO) else colorFor(LogLevel.WARN),
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun NoDevices(uiModel: SourcePickerUiModel) {
    Text(
        text = if (uiModel.isStarting) {
            "Starting the adb server"
        } else {
            "No devices attached. Plug one in over USB, or open a recording below."
        },
        style = JewelTheme.typography.small,
        color = JewelTheme.globalColors.text.info,
        textAlign = TextAlign.Center,
    )
    OutlinedButton(onClick = { uiModel.eventHandler(OnRetryClicked) }) { Text("Look again") }
}

@Composable
private fun AdbUnavailable(uiModel: SourcePickerUiModel) {
    Text(
        text = uiModel.installFailure ?: uiModel.unavailableReason.orEmpty(),
        style = JewelTheme.typography.small,
        color = colorFor(LogLevel.ERROR),
        textAlign = TextAlign.Center,
    )

    if (uiModel.isInstallingTools) {
        Text(
            text = if (uiModel.installProgress == null) "Unpacking the platform tools…" else "Downloading adb…",
            style = JewelTheme.typography.small,
            color = JewelTheme.globalColors.text.info,
        )
        HorizontalProgressBar(
            progress = uiModel.installProgress ?: 0f,
            modifier = Modifier.width(PROGRESS_WIDTH),
        )
        return
    }

    if (uiModel.canInstallTools) {
        Text(
            text = "No adb on this machine. HereKitty can fetch Google's platform tools for you.",
            style = JewelTheme.typography.small,
            color = JewelTheme.globalColors.text.info,
            textAlign = TextAlign.Center,
        )
        DefaultButton(onClick = { uiModel.eventHandler(OnInstallToolsClicked) }) { Text("Install adb") }
        OutlinedButton(onClick = { uiModel.eventHandler(OnRetryClicked) }) { Text("Look again") }
        return
    }

    DefaultButton(onClick = { uiModel.eventHandler(OnRetryClicked) }) { Text("Retry") }
}

private val PROGRESS_WIDTH = 220.dp
private val CARD_MAX_WIDTH = 380.dp
private val ROW_HEIGHT = 40.dp
