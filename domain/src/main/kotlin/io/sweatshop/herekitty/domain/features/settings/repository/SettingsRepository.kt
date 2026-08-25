package io.sweatshop.herekitty.domain.features.settings.repository

import io.sweatshop.herekitty.domain.features.settings.model.LogColumns
import io.sweatshop.herekitty.domain.features.settings.model.ThemeMode
import kotlinx.coroutines.flow.StateFlow

interface SettingsRepository {
    val memoryCapBytes: StateFlow<Long>
    val themeMode: StateFlow<ThemeMode>
    val logColumns: StateFlow<LogColumns>

    /** Multiplier on the log font, driven by the zoom shortcuts as well as the settings window. */
    val logFontScale: StateFlow<Float>

    /** Whether launching reopens the tabs, splits and views from last time. */
    val restoreLastLayout: StateFlow<Boolean>

    /** Whether closing a session with unsaved changes to a *named* view asks first. */
    val confirmSessionClose: StateFlow<Boolean>

    /** Whether quitting asks first. */
    val confirmExit: StateFlow<Boolean>

    /** Whether launching asks GitHub whether there is a newer release. */
    val checkForUpdatesOnStartup: StateFlow<Boolean>

    /** How long a notification waits before dismissing itself. Zero means it waits for you. */
    val notificationDismissSeconds: StateFlow<Int>

    /** Whether dragging across log rows selects only the message, not the timestamp/tag/etc. too. */
    val selectMessageOnly: StateFlow<Boolean>

    fun setMemoryCapBytes(bytes: Long)

    fun setThemeMode(mode: ThemeMode)

    fun setLogColumns(columns: LogColumns)

    fun setLogFontScale(scale: Float)

    fun setRestoreLastLayout(restore: Boolean)

    fun setConfirmSessionClose(confirm: Boolean)

    fun setConfirmExit(confirm: Boolean)

    fun setCheckForUpdatesOnStartup(check: Boolean)

    fun setNotificationDismissSeconds(seconds: Int)

    fun setSelectMessageOnly(messageOnly: Boolean)

    companion object {
        const val DEFAULT_MEMORY_CAP_BYTES: Long = 512L * 1024 * 1024
        const val DEFAULT_NOTIFICATION_DISMISS_SECONDS: Int = 8
        val NOTIFICATION_DISMISS_CHOICES: List<Int> = listOf(0, 3, 5, 8, 15, 30)

        val MEMORY_CAP_CHOICES: List<Long> =
            listOf(64L, 128L, 256L, 512L, 1024L, 2048L, 4096L).map { it * 1024 * 1024 }
    }
}
