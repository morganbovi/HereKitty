package io.sweatshop.herekitty.ui.theme

import androidx.compose.runtime.Composable
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.intui.standalone.theme.darkThemeDefinition
import org.jetbrains.jewel.intui.standalone.theme.lightThemeDefinition
import org.jetbrains.jewel.intui.window.decoratedWindow
import org.jetbrains.jewel.ui.ComponentStyling

/**
 * Wraps *windows*, not content, so it adds no layout of its own — a window cannot be nested inside a
 * Box. Content that needs the panel colour paints it itself.
 *
 * The styling has to include `decoratedWindow()`: the plain Int UI theme has no title bar styling, and
 * a decorated window fails outright without it.
 */
@Composable
fun HereKittyTheme(isDark: Boolean, content: @Composable () -> Unit) {
    val theme = if (isDark) JewelTheme.darkThemeDefinition() else JewelTheme.lightThemeDefinition()

    IntUiTheme(theme = theme, styling = ComponentStyling.decoratedWindow(), content = content)
}
