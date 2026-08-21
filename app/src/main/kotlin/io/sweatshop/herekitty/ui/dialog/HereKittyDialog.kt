package io.sweatshop.herekitty.ui.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import org.jetbrains.jewel.foundation.theme.JewelTheme

/**
 * A dialog that looks like the rest of the app.
 *
 * `DialogWindow` gets its own OS window, which arrives with no background of its own. Jewel's text
 * colours are then drawn onto whatever the platform painted, which on a dark theme reads as
 * light-on-light. Painting the panel background here fixes every dialog at once.
 */
@Composable
fun HereKittyDialog(
    title: String,
    size: DpSize,
    onCloseRequest: () -> Unit,
    resizable: Boolean = false,
    content: @Composable () -> Unit,
) {
    DialogWindow(
        onCloseRequest = onCloseRequest,
        state = rememberDialogState(size = size),
        title = title,
        resizable = resizable,
    ) {
        Box(Modifier.fillMaxSize().background(JewelTheme.globalColors.panelBackground)) {
            content()
        }
    }
}
