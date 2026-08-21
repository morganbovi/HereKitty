package io.sweatshop.herekitty.features.session.pane

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalViewConfiguration

/**
 * Pairs two clicks on the same tag into a double click.
 *
 * The pairing is held above the list rather than by the row that was clicked. Rows are recycled as
 * lines arrive, their metadata headers regroup around them, and following the tail scrolls the row out
 * from under the pointer between one click and the next — so the node that saw the first click is
 * routinely gone before the second, which is why a per-row double-click handler misses. Pairing on the
 * tag survives all three, and a single click only has to outlive its own press.
 */
internal class TagClickPairing(
    private val windowMillis: Long,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private var lastTag: String? = null
    private var lastClickMillis = 0L

    fun isDoubleClick(tag: String): Boolean {
        val now = nowMillis()
        val paired = tag == lastTag && now - lastClickMillis <= windowMillis
        lastTag = if (paired) null else tag
        lastClickMillis = now
        return paired
    }
}

@Composable
internal fun rememberTagClickPairing(): TagClickPairing {
    val doubleTapTimeoutMillis = LocalViewConfiguration.current.doubleTapTimeoutMillis
    return remember(doubleTapTimeoutMillis) { TagClickPairing(doubleTapTimeoutMillis) }
}
