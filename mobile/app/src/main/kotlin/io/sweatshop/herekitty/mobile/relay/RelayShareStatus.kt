package io.sweatshop.herekitty.mobile.relay

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.core.annotation.Single

/**
 * Bridging status text, readable by `SharePresenter` and written by `RelayShareService` -- a
 * plain Koin singleton rather than something exposed through binding to the service, since the UI
 * only ever needs to *read* it. Outliving the Activity is the entire point of the service, so its
 * status has to live somewhere that isn't Activity-scoped Compose state either.
 */
@Single
class RelayShareStatus {
    private val _status = MutableStateFlow("not shared")
    val status: StateFlow<String> = _status.asStateFlow()

    fun update(newStatus: String) {
        _status.value = newStatus
    }
}
