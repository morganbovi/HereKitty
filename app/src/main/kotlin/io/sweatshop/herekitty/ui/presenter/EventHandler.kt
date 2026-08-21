package io.sweatshop.herekitty.ui.presenter

data class EventHandler<E>(val key: Any? = null, val key1: Any? = null, val handle: (E) -> Unit = {}) {
    override fun equals(other: Any?): Boolean =
        this.key == (other as? EventHandler<*>)?.key && this.key1 == (other as? EventHandler<*>)?.key1

    override fun hashCode(): Int = handle.hashCode()

    operator fun invoke(event: E) = handle(event)
}
