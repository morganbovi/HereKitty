package io.sweatshop.herekitty.domain.features.views.model

/**
 * A named pane arrangement, deliberately free of any device or file. That is what lets the same view
 * be applied to a phone on one desk and to an imported recording on another.
 */
data class ViewConfig(
    val name: String,
    val root: PaneNode,
) {
    /** Every pane in layout order. Most callers want this rather than the tree. */
    val panes: List<PaneConfig> get() = root.leaves()

    val summary: String get() = panes.joinToString(" | ") { it.label }

    /** Ignores node ids and names, so only a real change to the arrangement counts as one. */
    fun hasSameSetupAs(other: ViewConfig): Boolean = root.describesSameAs(other.root)

    companion object {
        const val FILE_EXTENSION: String = "hkview"

        val Default: ViewConfig = ViewConfig(name = "", root = PaneNode.Leaf(PaneConfig(PaneId(0L))))

        fun of(vararg panes: PaneConfig): ViewConfig = ViewConfig("", panesRow(panes.toList()))

        /** Wraps a flat list as a row, which is what an older saved view amounts to. */
        fun panesRow(panes: List<PaneConfig>): PaneNode {
            if (panes.isEmpty()) return Default.root
            if (panes.size == 1) return PaneNode.Leaf(panes.single())

            val splitId = PaneId(panes.maxOf { it.id.value } + 1L)
            return PaneNode.Split(splitId, LayoutOrientation.Horizontal, panes.map { PaneNode.Leaf(it) })
        }
    }
}
