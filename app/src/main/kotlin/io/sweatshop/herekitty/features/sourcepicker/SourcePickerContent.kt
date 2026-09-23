package io.sweatshop.herekitty.features.sourcepicker

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.sweatshop.herekitty.domain.features.auth.AuthState
import io.sweatshop.herekitty.domain.features.devices.model.AdbDevice
import io.sweatshop.herekitty.domain.features.logs.model.LastSessionInfo
import io.sweatshop.herekitty.domain.features.logs.model.LogLevel
import io.sweatshop.herekitty.features.relay.RelayCardPresenter
import io.sweatshop.herekitty.features.relay.RelayCardUiModel
import io.sweatshop.herekitty.features.relay.RelayDeviceGridContent
import io.sweatshop.herekitty.features.relay.RelaySettingsPopup
import io.sweatshop.herekitty.features.sourcepicker.SourcePickerUiModel.Event.OnCloseClicked
import io.sweatshop.herekitty.features.sourcepicker.SourcePickerUiModel.Event.OnDeviceClicked
import io.sweatshop.herekitty.features.sourcepicker.SourcePickerUiModel.Event.OnRecordingChosen
import io.sweatshop.herekitty.features.sourcepicker.SourcePickerUiModel.Event.OnInstallToolsClicked
import io.sweatshop.herekitty.features.sourcepicker.SourcePickerUiModel.Event.OnRetryClicked
import io.sweatshop.herekitty.ui.component.IconAction
import io.sweatshop.herekitty.ui.files.FileDialogs
import io.sweatshop.herekitty.ui.format.formatCaptureDate
import io.sweatshop.herekitty.ui.theme.colorFor
import java.awt.Cursor
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.koin.compose.koinInject
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.HorizontalProgressBar
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.ui.typography

@Composable
fun SourcePickerContent(
    uiModel: SourcePickerUiModel,
    modifier: Modifier = Modifier,
    relayPresenter: RelayCardPresenter = koinInject(),
) {
    val relayUiModel = relayPresenter.present(
        onDeviceBridged = { uiModel.eventHandler(OnDeviceClicked(it)) },
    )

    Crossfade(targetState = relayUiModel.isBrowsing, modifier = modifier.fillMaxSize()) { browsing ->
        if (browsing) {
            RelayDeviceGridContent(relayUiModel, Modifier.fillMaxSize())
        } else {
            Box(Modifier.fillMaxSize()) {
                if (uiModel.canClose) {
                    IconAction(
                        key = AllIconsKeys.General.Close,
                        description = "Close this split",
                        onClick = { uiModel.eventHandler(OnCloseClicked) },
                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                    )
                }

                BoxWithConstraints(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .widthIn(max = CONTENT_MAX_WIDTH)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                ) {
                    // Four cards in a 2x2 grid once there is room for them; stacked otherwise, so a
                    // narrow split still gets the full picture instead of squeezed, unreadable columns.
                    if (maxWidth < CARDS_ROW_MIN_WIDTH) {
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            SourceCards(uiModel, relayUiModel, Modifier.fillMaxWidth())
                        }
                    } else {
                        val cards = sourceCardSlots(uiModel, relayUiModel)
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            cards.chunked(2).forEach { row ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    row.forEach { card -> card(Modifier.weight(1f).fillMaxHeight()) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * One call site building both layouts: each card gets the same per-card modifier (`weight(1f)` in a
 * row, `fillMaxWidth()` stacked), so the two arrangements cannot drift apart from each other.
 */
@Composable
private fun SourceCards(uiModel: SourcePickerUiModel, relayUiModel: RelayCardUiModel, cardModifier: Modifier) {
    sourceCardSlots(uiModel, relayUiModel).forEach { card -> card(cardModifier) }
}

@Composable
private fun sourceCardSlots(
    uiModel: SourcePickerUiModel,
    relayUiModel: RelayCardUiModel,
): List<@Composable (Modifier) -> Unit> = buildList {
    if (uiModel.lastSessions.isNotEmpty()) add { m -> LastSessionCard(uiModel, m) }
    add { m -> DeviceCard(uiModel, m) }
    add { m -> OpenFileCard(uiModel, m) }
    add { m -> RelayCard(relayUiModel, m) }
}

@Composable
private fun SourceCard(
    icon: IconKey?,
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    iconContent: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .shadow(elevation = CARD_ELEVATION, shape = RoundedCornerShape(CARD_CORNER_RADIUS))
            .clip(RoundedCornerShape(CARD_CORNER_RADIUS))
            // The page behind this is panelBackground too, so the shadow alone would be the only
            // thing telling the two apart — a faint tint on top of the same base gives the card its
            // own color without inventing one, and stays a step below the rows' own tint inside it.
            .background(JewelTheme.globalColors.panelBackground)
            .background(JewelTheme.globalColors.outlines.focused.copy(alpha = 0.05f))
            .padding(CARD_PADDING),
        horizontalAlignment = Alignment.CenterHorizontally,
        // content is invoked directly here rather than wrapped in its own child Column: when it emits
        // more than one composable (a status line plus a button, with no subtitle above), SpaceBetween
        // spreads the leftover space across every gap equally, which is what centers the status line
        // between the title and the button instead of leaving it pinned to one side.
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier.padding(bottom = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(ICON_BADGE_SIZE)
                    .clip(CircleShape)
                    .background(JewelTheme.globalColors.outlines.focused.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                iconContent?.invoke() ?: Icon(key = requireNotNull(icon), contentDescription = null)
            }

            Text(title, style = JewelTheme.typography.regular, fontWeight = FontWeight.Bold)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = JewelTheme.typography.small,
                    color = JewelTheme.globalColors.text.info,
                    textAlign = TextAlign.Center,
                )
            }
        }

        content()
    }
}

@Composable
private fun LastSessionCard(uiModel: SourcePickerUiModel, modifier: Modifier = Modifier) {
    SourceCard(
        icon = AllIconsKeys.General.History,
        title = "Open Last Session",
        subtitle = "Resume where you left off.",
        modifier = modifier,
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            uiModel.lastSessions.forEach { info ->
                LastSessionRow(
                    info = info,
                    config = uiModel.pendingViewLabel ?: "Default view",
                    onClick = { uiModel.eventHandler(OnRecordingChosen(info.path)) },
                )
            }
        }
    }
}

@Composable
private fun LastSessionRow(info: LastSessionInfo, config: String, onClick: () -> Unit) {
    // Stacked rather than a name-and-status row like a device, so it reads as a distinct kind of
    // thing to pick — a captured moment, not a live connection — even at a glance.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(JewelTheme.globalColors.outlines.focused.copy(alpha = 0.10f))
            .pointerHoverIcon(CLICKABLE_CURSOR)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = info.recordedFrom,
            style = JewelTheme.typography.regular,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
        )
        Text(
            text = "Captured ${formatCaptureDate(info.exportedAtMillis)}",
            style = JewelTheme.typography.small,
            color = JewelTheme.globalColors.text.info,
            maxLines = 1,
            softWrap = false,
        )
        Text(
            text = "View: $config",
            style = JewelTheme.typography.small,
            color = JewelTheme.globalColors.text.info,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
        )
    }
}

@Composable
private fun DeviceCard(uiModel: SourcePickerUiModel, modifier: Modifier = Modifier) {
    // NoDevices carries its own status line ("No devices attached…" / "Starting the adb server"), so
    // the generic subtitle would just repeat it — show one or the other, never both.
    val hasOwnStatusLine = uiModel.unavailableReason == null && uiModel.devices.isEmpty()
    SourceCard(
        icon = AllIconsKeys.General.Mouse,
        title = "Choose a Device",
        subtitle = if (hasOwnStatusLine) null else "Pick a connected device to start a new session.",
        modifier = modifier,
    ) {
        when {
            uiModel.unavailableReason != null -> AdbUnavailable(uiModel)
            uiModel.devices.isEmpty() -> NoDevices(uiModel)
            else -> DeviceList(uiModel)
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
            .clip(RoundedCornerShape(6.dp))
            .background(JewelTheme.globalColors.outlines.focused.copy(alpha = 0.10f))
            .then(if (isSelectable) Modifier.pointerHoverIcon(CLICKABLE_CURSOR).clickable(onClick = onClick) else Modifier)
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
    // Left as two direct children (not wrapped in their own Column) so SourceCard's SpaceBetween
    // splits the leftover space evenly around this line, centering it between the title and the
    // button instead of pinning it to either.
    Text(
        text = if (uiModel.isStarting) {
            "Starting the adb server"
        } else {
            "No devices attached. Plug one in over USB."
        },
        style = JewelTheme.typography.small,
        color = JewelTheme.globalColors.text.info,
        textAlign = TextAlign.Center,
    )
    OutlinedButton(onClick = { uiModel.eventHandler(OnRetryClicked) }) { Text("Refresh Devices") }
}

@Composable
private fun AdbUnavailable(uiModel: SourcePickerUiModel) {
    // Kept as one child of SourceCard's outer Column — unlike NoDevices, this can show several lines
    // plus one or two buttons, and splitting that many pieces across SpaceBetween's gaps would scatter
    // them instead of keeping them read as a single block.
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
            return@Column
        }

        if (uiModel.canInstallTools) {
            Text(
                text = "No adb on this machine. HereKitty can fetch Google's platform tools for you.",
                style = JewelTheme.typography.small,
                color = JewelTheme.globalColors.text.info,
                textAlign = TextAlign.Center,
            )
            DefaultButton(onClick = { uiModel.eventHandler(OnInstallToolsClicked) }) { Text("Install adb") }
            OutlinedButton(onClick = { uiModel.eventHandler(OnRetryClicked) }) { Text("Refresh Devices") }
            return@Column
        }

        DefaultButton(onClick = { uiModel.eventHandler(OnRetryClicked) }) { Text("Refresh Devices") }
    }
}

@Composable
private fun OpenFileCard(uiModel: SourcePickerUiModel, modifier: Modifier = Modifier) {
    SourceCard(
        icon = AllIconsKeys.Actions.Upload,
        title = "Open a File",
        subtitle = "Open a recording or bundle to debug offline.",
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .border(1.dp, JewelTheme.globalColors.borders.normal, RoundedCornerShape(6.dp))
                .clickable {
                    FileDialogs.openRecording()?.let { uiModel.eventHandler(OnRecordingChosen(it)) }
                }
                .padding(vertical = 14.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Icon(key = AllIconsKeys.Actions.Upload, contentDescription = null)
            Text(text = "Click to browse", style = JewelTheme.typography.small, fontWeight = FontWeight.Medium)
            Text(
                text = ".hklog.gz or .hkbundle",
                style = JewelTheme.typography.small,
                color = JewelTheme.globalColors.text.info,
            )
        }
    }
}

@Composable
private fun RelayCard(uiModel: RelayCardUiModel, modifier: Modifier = Modifier) {
    val state = uiModel.authState
    var isSettingsOpen by remember { mutableStateOf(false) }

    Box(modifier) {
        SourceCard(
            icon = null,
            title = "Relay",
            subtitle = "Use shared Android devices from anywhere.",
            modifier = Modifier.fillMaxSize(),
            iconContent = { RelayMark() },
        ) {
            if (uiModel.relayReachable == false) {
                Text(
                    text = "Relay service unavailable",
                    style = JewelTheme.typography.small,
                    color = colorFor(LogLevel.WARN),
                    textAlign = TextAlign.Center,
                )
            }
            when (state) {
                is AuthState.Authenticated -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (uiModel.orgId == null) {
                        Text(
                            text = "Not assigned to an org yet.",
                            style = JewelTheme.typography.small,
                            color = JewelTheme.globalColors.text.info,
                            textAlign = TextAlign.Center,
                        )
                    } else {
                        val onlineCount = uiModel.devices.count { it.isOnline }
                        Text(
                            text = if (uiModel.isLoadingDevices) "Checking devices…" else "$onlineCount device${if (onlineCount == 1) "" else "s"} online",
                            style = JewelTheme.typography.small,
                            color = JewelTheme.globalColors.text.info,
                        )
                        DefaultButton(onClick = { uiModel.eventHandler(RelayCardUiModel.Event.OnBrowseClicked) }) {
                            Text("Use Relay")
                        }
                    }
                }

                else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    DefaultButton(
                        enabled = !uiModel.isSigningIn,
                        onClick = { uiModel.eventHandler(RelayCardUiModel.Event.OnSignInClicked) },
                    ) {
                        Text(if (uiModel.isSigningIn) "Signing in…" else "Sign in with Google")
                    }
                    uiModel.error?.let { message ->
                        Text(
                            text = message,
                            style = JewelTheme.typography.small,
                            color = colorFor(LogLevel.ERROR),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }

        if (state is AuthState.Authenticated) {
            Box(modifier = Modifier.align(Alignment.TopEnd)) {
                IconAction(
                    key = AllIconsKeys.General.Settings,
                    description = "Relay settings",
                    onClick = { isSettingsOpen = true },
                    modifier = Modifier.padding(4.dp),
                )
                if (isSettingsOpen) {
                    RelaySettingsPopup(
                        signedInAs = state.displayName.ifBlank { state.email },
                        onSignOutClicked = { uiModel.eventHandler(RelayCardUiModel.Event.OnSignOutClicked) },
                        onDismissRequest = { isSettingsOpen = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun RelayMark() {
    val accent = JewelTheme.globalColors.outlines.focused
    Canvas(Modifier.size(26.dp)) {
        val hub = center
        val points = listOf(
            androidx.compose.ui.geometry.Offset(size.width * 0.2f, size.height * 0.3f),
            androidx.compose.ui.geometry.Offset(size.width * 0.8f, size.height * 0.3f),
            androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height * 0.82f),
        )
        points.forEach { point ->
            drawLine(accent, hub, point, strokeWidth = size.minDimension * 0.075f)
            drawCircle(accent, radius = size.minDimension * 0.14f, center = point, style = Stroke(size.minDimension * 0.07f))
        }
        drawCircle(accent, radius = size.minDimension * 0.18f, center = hub)
    }
}

private val PROGRESS_WIDTH = 220.dp
private val ROW_HEIGHT = 40.dp
private val CARD_PADDING = 16.dp
private val CARD_CORNER_RADIUS = 10.dp
private val CARD_ELEVATION = 3.dp
private val ICON_BADGE_SIZE = 40.dp
private val CONTENT_MAX_WIDTH = 760.dp
private val CARDS_ROW_MIN_WIDTH = 620.dp
private val CLICKABLE_CURSOR = PointerIcon(Cursor(Cursor.HAND_CURSOR))
