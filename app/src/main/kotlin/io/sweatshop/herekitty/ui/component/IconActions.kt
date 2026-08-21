package io.sweatshop.herekitty.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.jewel.ui.component.IconActionButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.ToggleableIconActionButton
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.icon.IconKey

/**
 * Icon buttons whose tooltip *is* their content description.
 *
 * Every control in this app is an icon, so each one needs to be able to say what it does. Taking one
 * string for both means the tooltip and the accessible name cannot drift apart, and that no icon can
 * be added without a description.
 */
@Composable
fun IconAction(
    key: IconKey,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Tooltip(tooltip = { Text(description) }) {
        IconActionButton(
            key = key,
            contentDescription = description,
            onClick = onClick,
            enabled = enabled,
            modifier = modifier,
        )
    }
}

@Composable
fun ToggleableIconAction(
    key: IconKey,
    description: String,
    value: Boolean,
    onValueChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Tooltip(tooltip = { Text(description) }) {
        ToggleableIconActionButton(
            key = key,
            contentDescription = description,
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            modifier = modifier,
        )
    }
}
