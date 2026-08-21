package io.sweatshop.herekitty.domain.features.devices.repository

import io.sweatshop.herekitty.domain.features.devices.model.AdbToolsState
import kotlinx.coroutines.flow.StateFlow

interface AdbToolsRepository {
    val state: StateFlow<AdbToolsState>

    /** Downloads Google's platform-tools into the app's own directory. */
    suspend fun install(): Result<Unit>

    fun refresh()
}
