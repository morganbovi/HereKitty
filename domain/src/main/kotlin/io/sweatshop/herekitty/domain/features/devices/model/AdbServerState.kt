package io.sweatshop.herekitty.domain.features.devices.model

sealed interface AdbServerState {
    data object Starting : AdbServerState

    data class Connected(val version: Int) : AdbServerState

    data class Unavailable(val reason: String) : AdbServerState
}
