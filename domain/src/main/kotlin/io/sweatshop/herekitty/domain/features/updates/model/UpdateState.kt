package io.sweatshop.herekitty.domain.features.updates.model

/** Where an update has got to. One of these at a time, for the whole app. */
sealed interface UpdateState {
    data object Idle : UpdateState

    data object Checking : UpdateState

    data class UpToDate(val version: AppVersion) : UpdateState

    data class Available(val release: ReleaseInfo) : UpdateState

    /** [fraction] is null until the download's size is known. */
    data class Downloading(val release: ReleaseInfo, val fraction: Float?) : UpdateState

    /** Staged and verified. Applying it from here quits the app and relaunches the new one. */
    data class ReadyToInstall(val release: ReleaseInfo, val stagedPath: String) : UpdateState

    data class Failed(val reason: String) : UpdateState
}
