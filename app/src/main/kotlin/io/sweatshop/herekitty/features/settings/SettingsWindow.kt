package io.sweatshop.herekitty.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.sweatshop.herekitty.app.HereKittyAppUiModel
import io.sweatshop.herekitty.app.HereKittyAppUiModel.Event.OnSettingsDismissed
import io.sweatshop.herekitty.features.updates.UpdateUiModel
import io.sweatshop.herekitty.ui.dialog.HereKittyDialog
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.typography


@Composable
fun SettingsWindow(uiModel: HereKittyAppUiModel, updateUiModel: UpdateUiModel) {
    var selected by remember { mutableStateOf(SettingsCategory.Appearance) }

    HereKittyDialog(
        title = "HereKitty Settings",
        size = WINDOW_SIZE,
        resizable = true,
        onCloseRequest = { uiModel.eventHandler(OnSettingsDismissed) },
    ) {
        Row(Modifier.fillMaxSize()) {
            CategoryList(selected = selected, onSelect = { selected = it })

            Divider(Orientation.Vertical, modifier = Modifier.fillMaxHeight())

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(selected.title, style = JewelTheme.typography.h3TextStyle)

                when (selected) {
                    SettingsCategory.Appearance -> AppearanceSettings(uiModel)
                    SettingsCategory.Updates -> UpdateSettings(uiModel, updateUiModel)
                    SettingsCategory.LogDisplay -> LogDisplaySettings(uiModel)
                    SettingsCategory.Recording -> RecordingSettings(uiModel)
                    SettingsCategory.Prompts -> PromptSettings(uiModel)
                }
            }
        }
    }
}

@Composable
private fun CategoryList(selected: SettingsCategory, onSelect: (SettingsCategory) -> Unit) {
    Column(Modifier.width(SIDEBAR_WIDTH).fillMaxHeight().padding(vertical = 6.dp)) {
        SettingsCategory.entries.forEach { category ->
            val isSelected = category == selected
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CATEGORY_ROW_HEIGHT)
                    .background(
                        if (isSelected) {
                            JewelTheme.globalColors.outlines.focused.copy(alpha = 0.20f)
                        } else {
                            JewelTheme.globalColors.panelBackground
                        },
                    )
                    .clickable { onSelect(category) }
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(category.icon, contentDescription = null)
                Text(
                    text = category.title,
                    style = JewelTheme.typography.regular,
                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

private val WINDOW_SIZE = DpSize(660.dp, 460.dp)
private val SIDEBAR_WIDTH = 170.dp
private val CATEGORY_ROW_HEIGHT = 28.dp
