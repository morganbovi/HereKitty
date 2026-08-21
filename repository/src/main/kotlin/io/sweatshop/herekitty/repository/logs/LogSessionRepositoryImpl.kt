package io.sweatshop.herekitty.repository.logs

import io.sweatshop.herekitty.adb.AdbHostClient
import io.sweatshop.herekitty.domain.base.AppScope
import io.sweatshop.herekitty.domain.features.devices.model.AdbDevice
import io.sweatshop.herekitty.domain.features.devices.model.DeviceState
import io.sweatshop.herekitty.domain.features.devices.repository.DeviceRepository
import io.sweatshop.herekitty.domain.features.logs.model.SessionSource
import io.sweatshop.herekitty.domain.features.logs.repository.LogSession
import io.sweatshop.herekitty.domain.features.logs.repository.LogSessionRepository
import io.sweatshop.herekitty.domain.features.logs.repository.SessionId
import io.sweatshop.herekitty.domain.features.settings.repository.SettingsRepository
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single

@Single(binds = [LogSessionRepository::class])
class LogSessionRepositoryImpl(
    private val hostClient: AdbHostClient,
    private val deviceRepository: DeviceRepository,
    private val settingsRepository: SettingsRepository,
    private val appScope: AppScope,
) : LogSessionRepository {

    private val mutableSessions = MutableStateFlow<List<LogSession>>(emptyList())
    override val sessions = mutableSessions.asStateFlow()

    private val nextId = AtomicLong(1L)

    init {
        appScope.launch {
            deviceRepository.devices.collect { devices ->
                val bySerial = devices.associateBy { it.serial }
                mutableSessions.value.filterIsInstance<DeviceLogSession>().forEach { session ->
                    session.updateDevice(bySerial[session.serial] ?: session.lastKnownDevice().asAbsent())
                }
            }
        }
    }

    override fun open(device: AdbDevice): LogSession {
        val session = DeviceLogSession(
            id = SessionId(nextId.getAndIncrement()),
            initialDevice = device,
            hostClient = hostClient,
            memoryCapBytes = settingsRepository.memoryCapBytes,
            parentScope = appScope,
        )
        mutableSessions.value = mutableSessions.value + session
        return session
    }

    override suspend fun openRecording(path: Path): Result<LogSession> = runCatching {
        val header = withContext(Dispatchers.IO) {
            if (SessionBundleFiles.looksLikeBundle(path)) {
                SessionBundleFiles.readHeader(path)
            } else {
                RecordingFiles.readHeader(path)
            }
        }
        val session = RecordedLogSession(
            id = SessionId(nextId.getAndIncrement()),
            path = path,
            header = header,
            memoryCapBytes = settingsRepository.memoryCapBytes,
            parentScope = appScope,
        )
        mutableSessions.value = mutableSessions.value + session
        session
    }

    override fun close(id: SessionId) {
        val session = mutableSessions.value.firstOrNull { it.id == id } ?: return
        mutableSessions.value = mutableSessions.value - session
        (session as? BaseLogSession)?.dispose()
    }

    override fun session(id: SessionId): LogSession? = mutableSessions.value.firstOrNull { it.id == id }

    private fun DeviceLogSession.lastKnownDevice(): AdbDevice =
        (source.value as? SessionSource.Device)?.device
            ?: AdbDevice(serial, DeviceState.OFFLINE)

    private fun AdbDevice.asAbsent(): AdbDevice =
        if (state == DeviceState.OFFLINE) this else copy(state = DeviceState.OFFLINE)
}
