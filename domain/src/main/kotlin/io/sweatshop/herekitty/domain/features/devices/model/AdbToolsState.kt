package io.sweatshop.herekitty.domain.features.devices.model

/** Whether the machine has an adb to talk to, and how an install of one is going. */
sealed interface AdbToolsState {
    data object Present : AdbToolsState

    data object Missing : AdbToolsState

    /** [fraction] is null while the download's size is still unknown, and while unpacking. */
    data class Installing(val fraction: Float?) : AdbToolsState

    data class Failed(val reason: String) : AdbToolsState
}
