package io.sweatshop.herekitty.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.sweatshop.herekitty.app.HereKittyAppUiModel.Event.OnSettingsOpened
import io.sweatshop.herekitty.domain.features.logs.model.LogLevel
import io.sweatshop.herekitty.ui.component.IconAction
import io.sweatshop.herekitty.ui.theme.colorFor
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.ui.typography
import org.jetbrains.jewel.window.TitleBarScope

/**
 * What sits in the window's own title bar. The app has no separate top bar: its two global controls
 * live up here, which is the row the platform was going to draw anyway.
 */
@Composable
fun TitleBarScope.AppTitleBarContent(uiModel: HereKittyAppUiModel) {
    Row(
        modifier = Modifier.align(Alignment.Start).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = uiModel.adbStatus,
            style = JewelTheme.typography.small,
            color = if (uiModel.isAdbHealthy) JewelTheme.globalColors.text.info else colorFor(LogLevel.WARN),
            maxLines = 1,
        )
    }

    Text(title, style = JewelTheme.typography.regular, maxLines = 1)

    Row(
        modifier = Modifier.align(Alignment.End).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconAction(
            key = AllIconsKeys.General.Settings,
            description = "Settings",
            onClick = { uiModel.eventHandler(OnSettingsOpened) },
        )
    }
}
