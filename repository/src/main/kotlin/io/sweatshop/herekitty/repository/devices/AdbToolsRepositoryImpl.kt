package io.sweatshop.herekitty.repository.devices

import io.sweatshop.herekitty.adb.AdbBinaryLocator
import io.sweatshop.herekitty.adb.PlatformToolsInstaller
import io.sweatshop.herekitty.domain.base.Log
import io.sweatshop.herekitty.domain.features.devices.model.AdbToolsState
import io.sweatshop.herekitty.domain.features.devices.repository.AdbToolsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.core.annotation.Single

@Single(binds = [AdbToolsRepository::class])
class AdbToolsRepositoryImpl(
    private val installer: PlatformToolsInstaller,
    private val locator: AdbBinaryLocator,
) : AdbToolsRepository {

    private val _state = MutableStateFlow(whatIsThere())
    override val state = _state.asStateFlow()

    override suspend fun install(): Result<Unit> {
        _state.value = AdbToolsState.Installing(fraction = null)

        return try {
            installer.install { fraction -> _state.value = AdbToolsState.Installing(fraction) }
            _state.value = AdbToolsState.Present
            Result.success(Unit)
        } catch (e: CancellationException) {
            _state.value = whatIsThere()
            throw e
        } catch (e: Exception) {
            Log.e(e) { "Could not install the platform tools" }
            _state.value = AdbToolsState.Failed(e.message ?: "Could not install the platform tools")
            Result.failure(e)
        }
    }

    override fun refresh() {
        _state.value = whatIsThere()
    }

    private fun whatIsThere(): AdbToolsState =
        if (locator.locate() != null) AdbToolsState.Present else AdbToolsState.Missing
}
