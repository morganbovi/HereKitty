package io.sweatshop.herekitty.domain.features.workspace.model

import io.sweatshop.herekitty.domain.features.views.model.LayoutOrientation
import io.sweatshop.herekitty.domain.features.views.model.ViewConfig

/**
 * The workspace as it was last left: the tabs, their splits, and each slot's view.
 *
 * Recorded log lines are not part of it — they are only kept when a session is exported. A slot
 * remembers the serial it was attached to so the setup can pick up where it left off if that device
 * is still plugged in.
 */
data class StoredLayout(val tabs: List<StoredTab>) {
    val isEmpty: Boolean get() = tabs.isEmpty()

    companion object {
        val Empty: StoredLayout = StoredLayout(emptyList())
    }
}

data class StoredTab(val root: StoredNode)

sealed interface StoredNode {
    data class Slot(val view: ViewConfig, val deviceSerial: String?) : StoredNode

    data class Split(
        val orientation: LayoutOrientation,
        val children: List<StoredNode>,
    ) : StoredNode
}
