package io.sweatshop.herekitty.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.sweatshop.herekitty.app.HereKittyAppUiModel
import io.sweatshop.herekitty.app.HereKittyAppUiModel.Event.OnConfirmExitChanged
import io.sweatshop.herekitty.app.HereKittyAppUiModel.Event.OnNotificationDismissSecondsChanged
import io.sweatshop.herekitty.app.HereKittyAppUiModel.Event.OnConfirmSessionCloseChanged
import io.sweatshop.herekitty.app.HereKittyAppUiModel.Event.OnLogColumnsChanged
import io.sweatshop.herekitty.app.HereKittyAppUiModel.Event.OnLogFontScaleChanged
import io.sweatshop.herekitty.app.HereKittyAppUiModel.Event.OnMemoryCapChosen
import io.sweatshop.herekitty.app.HereKittyAppUiModel.Event.OnRestoreLastLayoutChanged
import io.sweatshop.herekitty.app.HereKittyAppUiModel.Event.OnThemeModeChanged
import io.sweatshop.herekitty.domain.features.settings.model.LogFontScale
import io.sweatshop.herekitty.domain.features.settings.model.LogLineLayout
import io.sweatshop.herekitty.domain.features.settings.model.ThemeMode
import io.sweatshop.herekitty.domain.features.settings.repository.SettingsRepository
import io.sweatshop.herekitty.ui.format.formatBytes
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.ListComboBox
import org.jetbrains.jewel.ui.component.RadioButtonRow
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.typography

private val CAP_CHOICES = SettingsRepository.MEMORY_CAP_CHOICES
private val CAP_LABELS = CAP_CHOICES.map(::formatBytes)
private val LAYOUT_CHOICES = listOf(LogLineLayout.Columns, LogLineLayout.Stacked)
private val LAYOUT_LABELS = listOf("Columns", "Stacked")

@Composable
internal fun AppearanceSettings(uiModel: HereKittyAppUiModel) {
    ThemeMode.entries.forEach { mode ->
        RadioButtonRow(
            text = mode.label,
            selected = uiModel.themeMode == mode,
            onClick = { uiModel.eventHandler(OnThemeModeChanged(mode)) },
        )
    }
    Hint("Matching the system follows it as it changes, without needing a restart.")
}

@Composable
internal fun LogDisplaySettings(uiModel: HereKittyAppUiModel) {
    val columns = uiModel.logColumns

    SettingRow("Text size") {
        ListComboBox(
            items = FONT_SCALE_LABELS,
            selectedIndex = nearestFontScaleIndex(uiModel.logFontScale),
            onSelectedItemChange = {
                uiModel.eventHandler(OnLogFontScaleChanged(LogFontScale.Steps[it]))
            },
            modifier = Modifier.width(CONTROL_WIDTH),
        )
    }
    Hint("Cmd + and Cmd - change this from anywhere in the app, and Cmd 0 puts it back to 100%.")

    SettingRow("Arrange each line") {
        ListComboBox(
            items = LAYOUT_LABELS,
            selectedIndex = LAYOUT_CHOICES.indexOf(columns.layout).coerceAtLeast(0),
            onSelectedItemChange = {
                uiModel.eventHandler(OnLogColumnsChanged(columns.copy(layout = LAYOUT_CHOICES[it])))
            },
            modifier = Modifier.width(CONTROL_WIDTH),
        )
    }
    Hint("Stacked puts a tiny tag and time line above each message, so the message gets the pane's full width.")

    CheckboxRow(
        text = "Timestamp",
        checked = columns.timestamp,
        onCheckedChange = { uiModel.eventHandler(OnLogColumnsChanged(columns.copy(timestamp = it))) },
    )
    CheckboxRow(
        text = "Level letter",
        checked = columns.level,
        onCheckedChange = { uiModel.eventHandler(OnLogColumnsChanged(columns.copy(level = it))) },
    )
    CheckboxRow(
        text = "Tag",
        checked = columns.tag,
        onCheckedChange = { uiModel.eventHandler(OnLogColumnsChanged(columns.copy(tag = it))) },
    )
    CheckboxRow(
        text = "Process and thread id",
        checked = columns.processIds,
        onCheckedChange = { uiModel.eventHandler(OnLogColumnsChanged(columns.copy(processIds = it))) },
    )
    CheckboxRow(
        text = "Wrap long lines",
        checked = columns.softWrap,
        onCheckedChange = { uiModel.eventHandler(OnLogColumnsChanged(columns.copy(softWrap = it))) },
    )
    Hint("A pane watching several tags always shows the tag, whatever this says, so its lines can be told apart.")
}

@Composable
internal fun RecordingSettings(uiModel: HereKittyAppUiModel) {
    SettingRow("Memory per session") {
        ListComboBox(
            items = CAP_LABELS,
            selectedIndex = CAP_CHOICES.indexOf(uiModel.memoryCapBytes).coerceAtLeast(0),
            onSelectedItemChange = { uiModel.eventHandler(OnMemoryCapChosen(CAP_CHOICES[it])) },
            modifier = Modifier.width(CONTROL_WIDTH),
        )
    }
    Hint("Oldest lines are dropped once a session reaches its limit. Tags stay listed even after their lines go.")

    CheckboxRow(
        text = "Reopen last tabs and views on launch",
        checked = uiModel.restoreLastLayout,
        onCheckedChange = { uiModel.eventHandler(OnRestoreLastLayoutChanged(it)) },
    )
    Hint("Recorded lines are never reopened; only the layout and its filters come back.")
}

@Composable
internal fun PromptSettings(uiModel: HereKittyAppUiModel) {
    CheckboxRow(
        text = "Ask before closing a session with unsaved changes",
        checked = uiModel.confirmSessionClose,
        onCheckedChange = { uiModel.eventHandler(OnConfirmSessionCloseChanged(it)) },
    )
    Hint("Only asked for a view you have named and then changed. An unnamed setup closes without a word.")

    CheckboxRow(
        text = "Ask before quitting",
        checked = uiModel.confirmExit,
        onCheckedChange = { uiModel.eventHandler(OnConfirmExitChanged(it)) },
    )
    Hint("Quitting discards every recording that has not been exported.")

    SettingRow("Dismiss notifications after") {
        ListComboBox(
            items = DISMISS_LABELS,
            selectedIndex = SettingsRepository.NOTIFICATION_DISMISS_CHOICES
                .indexOf(uiModel.notificationDismissSeconds)
                .coerceAtLeast(0),
            onSelectedItemChange = {
                uiModel.eventHandler(
                    OnNotificationDismissSecondsChanged(
                        SettingsRepository.NOTIFICATION_DISMISS_CHOICES[it],
                    ),
                )
            },
            modifier = Modifier.width(CONTROL_WIDTH),
        )
    }
    Hint("Errors always wait to be dismissed, however this is set, so one cannot vanish before it is read.")
}

@Composable
private fun SettingRow(label: String, control: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, style = JewelTheme.typography.regular, modifier = Modifier.weight(1f))
        control()
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        style = JewelTheme.typography.small,
        color = JewelTheme.globalColors.text.info,
    )
}

private val CONTROL_WIDTH = 110.dp

private val FONT_SCALE_LABELS: List<String> =
    LogFontScale.Steps.map { "${(it * 100).toInt()}%" }

/** The stored scale can sit between rungs, so the combo shows the closest one rather than nothing. */
private fun nearestFontScaleIndex(scale: Float): Int =
    LogFontScale.Steps.indices.minByOrNull { kotlin.math.abs(LogFontScale.Steps[it] - scale) } ?: 0

private val DISMISS_LABELS: List<String> = SettingsRepository.NOTIFICATION_DISMISS_CHOICES
    .map { if (it == 0) "Never \u2014 wait for me" else "$it seconds" }
