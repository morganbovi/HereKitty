package io.sweatshop.herekitty.ui.notification

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.sweatshop.herekitty.domain.features.logs.model.LogLevel
import io.sweatshop.herekitty.ui.component.IconAction
import io.sweatshop.herekitty.ui.theme.colorFor
import kotlinx.coroutines.delay
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.ui.typography

/**
 * The stack of balloons in the bottom-right corner, the way IntelliJ reports things.
 *
 * Floats over the workspace rather than sitting in the layout, so a notice arriving does not shove
 * the panes down and back up again — which is what the full-width banner used to do.
 */
@Composable
fun NotificationHost(
    notifications: List<AppNotification>,
    dismissAfterSeconds: Int,
    onDismiss: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        notifications.forEach { notification ->
            // Keyed so a dismissal in the middle of the stack does not restart its neighbours' timers.
            key(notification.id) {
                NotificationBalloon(notification, dismissAfterSeconds, onDismiss)
            }
        }
    }
}

@Composable
private fun NotificationBalloon(
    notification: AppNotification,
    dismissAfterSeconds: Int,
    onDismiss: (Long) -> Unit,
) {
    val dismissesOnItsOwn = dismissAfterSeconds > 0 && notification.dismissesOnItsOwn

    if (dismissesOnItsOwn) {
        LaunchedEffect(notification.id, dismissAfterSeconds) {
            delay(dismissAfterSeconds * 1_000L)
            onDismiss(notification.id)
        }
    }

    val isError = notification.severity == NotificationSeverity.Error
    val accent = if (isError) colorFor(LogLevel.ERROR) else JewelTheme.globalColors.outlines.focused

    Row(
        modifier = Modifier
            .widthIn(max = BALLOON_MAX_WIDTH)
            .clip(BALLOON_SHAPE)
            .background(JewelTheme.globalColors.panelBackground)
            // Tinted as well as outlined: against a panel of the same colour, a border alone reads as
            // part of the layout rather than as something that just arrived.
            .background(accent.copy(alpha = TINT_ALPHA))
            .border(BORDER_WIDTH, accent.copy(alpha = BORDER_ALPHA), BALLOON_SHAPE)
            .padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            key = if (isError) AllIconsKeys.General.BalloonError else AllIconsKeys.General.BalloonInformation,
            contentDescription = if (isError) "Error" else "Notice",
            modifier = Modifier.size(ICON_SIZE),
        )

        Text(
            text = notification.message,
            style = JewelTheme.typography.regular,
            color = JewelTheme.globalColors.text.normal,
            modifier = Modifier.widthIn(max = TEXT_MAX_WIDTH),
        )

        IconAction(
            key = AllIconsKeys.General.CloseSmall,
            description = "Dismiss",
            onClick = { onDismiss(notification.id) },
        )
    }
}

private val BALLOON_SHAPE = RoundedCornerShape(8.dp)
private val BALLOON_MAX_WIDTH = 520.dp
private val TEXT_MAX_WIDTH = 400.dp
private val BORDER_WIDTH = 2.dp
private val ICON_SIZE = 20.dp
private const val BORDER_ALPHA = 0.8f
private const val TINT_ALPHA = 0.10f
