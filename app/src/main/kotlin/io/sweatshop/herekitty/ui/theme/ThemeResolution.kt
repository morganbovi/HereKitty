package io.sweatshop.herekitty.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import io.sweatshop.herekitty.domain.features.settings.model.ThemeMode

/**
 * Turns a stored preference into the theme to actually draw.
 *
 * Has to be resolved in composition rather than in the repository: [isSystemInDarkTheme] watches the
 * OS setting, so following the system means recomposing when the user changes it.
 */
@Composable
fun resolveDarkTheme(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
    ThemeMode.System -> isSystemInDarkTheme()
}
