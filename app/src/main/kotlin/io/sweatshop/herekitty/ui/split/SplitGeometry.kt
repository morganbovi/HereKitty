package io.sweatshop.herekitty.ui.split

/**
 * Where each child ended up along the split's main axis.
 *
 * Written during layout and read from drag callbacks, so it is deliberately not snapshot state:
 * reordering must not depend on a recomposition having happened first.
 */
internal class SplitGeometry {
    var sizes: IntArray = IntArray(0)
        private set
    var dividerSize: Int = 0
        private set
    var totalMainAxis: Int = 0
        private set

    fun record(sizes: IntArray, dividerSize: Int, totalMainAxis: Int) {
        this.sizes = sizes
        this.dividerSize = dividerSize
        this.totalMainAxis = totalMainAxis
    }

    fun centerOf(index: Int): Float {
        if (index !in sizes.indices) return 0f
        return offsetOf(index) + sizes[index] / 2f
    }

    fun indexAt(position: Float): Int {
        if (sizes.isEmpty()) return -1
        var edge = 0f
        for (index in sizes.indices) {
            edge += sizes[index] + if (index < sizes.size - 1) dividerSize else 0
            if (position < edge) return index
        }
        return sizes.size - 1
    }

    private fun offsetOf(index: Int): Float {
        var offset = 0f
        for (before in 0 until index) offset += sizes[before] + dividerSize
        return offset
    }
}
