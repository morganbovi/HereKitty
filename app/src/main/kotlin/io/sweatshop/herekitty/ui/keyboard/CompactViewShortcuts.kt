package io.sweatshop.herekitty.ui.keyboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import io.sweatshop.herekitty.domain.features.settings.repository.SettingsRepository
import org.koin.compose.koinInject

/**
 * Window-level handling for exiting compact view with Escape.
 *
 * There is deliberately no key that *enters* compact view. An unmodified `S` was tried first, but
 * [DecoratedWindow][org.jetbrains.jewel.window.DecoratedWindow]'s `onKeyEvent` sees every key-down at
 * the window regardless of what has focus — unlike the zoom shortcuts, which are safe only because
 * they require a modifier no text field's own typing ever produces. A bare letter has no such
 * protection: typing "s" into any search box toggled the view out from under it. Making that safe
 * would mean tracking "nothing editable has focus" globally across a native AWT-backed window, which
 * is exactly the kind of fragile plumbing not worth it for one convenience key.
 *
 * Escape survives because it is only ever consumed while compact view is already on, so it never
 * steals Escape from anything else when compact view is off.
 */
@Composable
fun rememberCompactViewShortcuts(settings: SettingsRepository = koinInject()): (KeyEvent) -> Boolean {
    val isCompactView by settings.isCompactView.collectAsState()
    val currentlyCompact by rememberUpdatedState(isCompactView)

    return remember(settings) {
        { event ->
            if (event.type == KeyEventType.KeyDown && event.key == Key.Escape && currentlyCompact) {
                settings.setCompactView(false)
                true
            } else {
                false
            }
        }
    }
}
