package io.sweatshop.herekitty.ui.presenter

data class SingleEventHandler(val key: Any? = null, val key1: Any? = null, val handle: () -> Unit = {}) {
    override fun equals(other: Any?): Boolean =
        this.key == (other as? SingleEventHandler)?.key && this.key1 == (other as? SingleEventHandler)?.key1

    override fun hashCode(): Int = handle.hashCode()

    operator fun invoke() = handle()
}
