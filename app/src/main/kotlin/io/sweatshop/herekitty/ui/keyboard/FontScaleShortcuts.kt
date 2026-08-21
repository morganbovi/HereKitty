package io.sweatshop.herekitty.ui.keyboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import io.sweatshop.herekitty.domain.features.settings.model.LogFontScale
import io.sweatshop.herekitty.domain.features.settings.repository.SettingsRepository
import org.koin.compose.koinInject

internal enum class FontScaleCommand { Increase, Decrease, Reset }

/**
 * `⌘+` is really `⌘=` on most layouts, because `+` needs shift — so both are accepted, along with the
 * keypad's own keys. Ctrl counts too, since that is the same shortcut everywhere but macOS.
 */
internal fun fontScaleCommandFor(key: Key, isCommandPressed: Boolean): FontScaleCommand? {
    if (!isCommandPressed) return null

    return when (key) {
        Key.Equals, Key.Plus, Key.NumPadAdd -> FontScaleCommand.Increase
        Key.Minus, Key.NumPadSubtract -> FontScaleCommand.Decrease
        Key.Zero, Key.NumPad0 -> FontScaleCommand.Reset
        else -> null
    }
}

/**
 * Window-level handling for the zoom shortcuts, returning true for the events it consumed.
 *
 * On the window rather than the panes so it works wherever the focus happens to be, and on key *down*
 * so holding the combo repeats instead of firing twice per press.
 */
@Composable
fun rememberFontScaleShortcuts(settings: SettingsRepository = koinInject()): (KeyEvent) -> Boolean {
    val scale by settings.logFontScale.collectAsState()
    val currentScale by rememberUpdatedState(scale)

    return remember(settings) {
        { event ->
            val command = if (event.type == KeyEventType.KeyDown) {
                fontScaleCommandFor(event.key, event.isMetaPressed || event.isCtrlPressed)
            } else {
                null
            }

            when (command) {
                FontScaleCommand.Increase -> settings.setLogFontScale(LogFontScale.increased(currentScale))
                FontScaleCommand.Decrease -> settings.setLogFontScale(LogFontScale.decreased(currentScale))
                FontScaleCommand.Reset -> settings.setLogFontScale(LogFontScale.Default)
                null -> Unit
            }

            command != null
        }
    }
}
