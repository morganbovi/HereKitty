package io.sweatshop.herekitty.domain.features.logs.repository

import io.sweatshop.herekitty.domain.features.devices.model.AdbDevice
import java.nio.file.Path
import kotlinx.coroutines.flow.StateFlow

interface LogSessionRepository {
    val sessions: StateFlow<List<LogSession>>

    fun open(device: AdbDevice): LogSession

    /** Loads a previously exported recording as a session that no longer captures. */
    suspend fun openRecording(path: Path): Result<LogSession>

    fun close(id: SessionId)

    fun session(id: SessionId): LogSession?
}
