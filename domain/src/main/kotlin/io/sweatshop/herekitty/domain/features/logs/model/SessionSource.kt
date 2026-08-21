package io.sweatshop.herekitty.domain.features.logs.model

import io.sweatshop.herekitty.domain.features.devices.model.AdbDevice
import java.nio.file.Path

/** Where a session's lines come from. A view config can be applied to either kind. */
sealed interface SessionSource {
    val label: String
    val detail: String

    data class Device(val device: AdbDevice) : SessionSource {
        override val label: String get() = device.displayName
        override val detail: String get() = device.serial
    }

    data class Recording(
        val path: Path,
        val recordedFrom: String,
        val recordedLineCount: Long,
    ) : SessionSource {
        override val label: String get() = recordedFrom
        override val detail: String get() = path.fileName?.toString().orEmpty()
    }
}
