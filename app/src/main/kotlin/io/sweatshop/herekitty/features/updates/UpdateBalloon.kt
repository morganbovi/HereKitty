package io.sweatshop.herekitty.features.updates

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.sweatshop.herekitty.domain.features.logs.model.LogLevel
import io.sweatshop.herekitty.domain.features.updates.model.UpdateState
import io.sweatshop.herekitty.features.updates.UpdateUiModel.Event.OnDismissed
import io.sweatshop.herekitty.features.updates.UpdateUiModel.Event.OnDownloadRequested
import io.sweatshop.herekitty.features.updates.UpdateUiModel.Event.OnRestartRequested
import io.sweatshop.herekitty.ui.component.IconAction
import io.sweatshop.herekitty.ui.theme.colorFor
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.HorizontalProgressBar
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.typography

/**
 * The update notice, in the same corner as the others and built the same way.
 *
 * Deliberately not routed through the notification queue: this one has buttons and a lifetime tied to
 * an ongoing operation, where a notification is a message that has already happened. Sharing the
 * corner is a layout decision, not a reason to share the type.
 */
@Composable
fun UpdateBalloon(uiModel: UpdateUiModel) {
    if (!uiModel.isWorthShowing) return

    val state = uiModel.state
    val isError = state is UpdateState.Failed
    val accent = if (isError) colorFor(LogLevel.ERROR) else JewelTheme.globalColors.outlines.focused

    Row(
        modifier = Modifier
            .widthIn(max = 520.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(JewelTheme.globalColors.panelBackground)
            .background(accent.copy(alpha = 0.10f))
            .border(2.dp, accent.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
            .padding(start = 14.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(
            modifier = Modifier.widthIn(max = 340.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(headline(uiModel), style = JewelTheme.typography.regular)

            detail(uiModel)?.let {
                Text(it, style = JewelTheme.typography.small, color = JewelTheme.globalColors.text.info)
            }

            (state as? UpdateState.Downloading)?.let {
                HorizontalProgressBar(progress = it.fraction ?: 0f, modifier = Modifier.width(220.dp))
            }
        }

        when (state) {
            is UpdateState.Available -> DefaultButton(
                onClick = { uiModel.eventHandler(OnDownloadRequested) },
            ) { Text("Download") }

            is UpdateState.ReadyToInstall -> DefaultButton(
                onClick = { uiModel.eventHandler(OnRestartRequested) },
            ) { Text("Restart") }

            else -> Unit
        }

        IconAction(
            key = org.jetbrains.jewel.ui.icons.AllIconsKeys.General.CloseSmall,
            description = "Dismiss",
            onClick = { uiModel.eventHandler(OnDismissed) },
        )
    }
}

private fun headline(uiModel: UpdateUiModel): String = when (val state = uiModel.state) {
    is UpdateState.Available -> "HereKitty ${state.release.version} is available"
    is UpdateState.Downloading -> "Downloading ${state.release.version}"
    is UpdateState.ReadyToInstall -> "${state.release.version} is ready to install"
    is UpdateState.UpToDate -> "HereKitty ${state.version} is up to date"
    is UpdateState.Failed -> "Could not check for updates"
    UpdateState.Checking -> "Checking for updates"
    UpdateState.Idle -> ""
}

private fun detail(uiModel: UpdateUiModel): String? = when (val state = uiModel.state) {
    is UpdateState.Available -> "You are running ${uiModel.runningVersion}."
    is UpdateState.ReadyToInstall -> "Restarting will replace this copy and reopen it."
    is UpdateState.Failed -> state.reason
    else -> null
}
