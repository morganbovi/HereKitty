package io.sweatshop.herekitty.domain.features.logs.model

sealed interface ConnectionState {
    data object Connecting : ConnectionState

    data object Streaming : ConnectionState

    data object Paused : ConnectionState

    data class Waiting(val reason: String) : ConnectionState

    data class Failed(val reason: String) : ConnectionState

    /** A recording loaded from a file: complete, and never going to change. */
    data object Recorded : ConnectionState

    val isLive: Boolean get() = this is Streaming

    val label: String
        get() = when (this) {
            Connecting -> "Connecting"
            Streaming -> "Streaming"
            Paused -> "Paused"
            Recorded -> "Recorded"
            is Waiting -> reason
            is Failed -> reason
        }
}
