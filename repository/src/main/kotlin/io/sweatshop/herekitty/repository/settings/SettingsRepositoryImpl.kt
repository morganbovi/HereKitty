package io.sweatshop.herekitty.repository.settings

import io.sweatshop.herekitty.domain.base.AppScope
import io.sweatshop.herekitty.domain.base.Log
import io.sweatshop.herekitty.domain.features.settings.model.LogColumns
import io.sweatshop.herekitty.domain.features.settings.model.LogFontScale
import io.sweatshop.herekitty.domain.features.settings.model.LogLineLayout
import io.sweatshop.herekitty.domain.features.settings.model.ThemeMode
import io.sweatshop.herekitty.domain.features.settings.repository.SettingsRepository
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single

@Single(binds = [SettingsRepository::class])
class SettingsRepositoryImpl(private val appScope: AppScope) : SettingsRepository {

    private val file: Path = Path.of(System.getProperty("user.home"), ".herekitty", "settings.json")
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    private val stored = load()

    private val _memoryCapBytes = MutableStateFlow(stored.memoryCapBytes)
    override val memoryCapBytes = _memoryCapBytes.asStateFlow()

    private val _themeMode = MutableStateFlow(stored.toThemeMode())
    override val themeMode = _themeMode.asStateFlow()

    private val _logColumns = MutableStateFlow(stored.toLogColumns())
    override val logColumns = _logColumns.asStateFlow()

    private val _logFontScale = MutableStateFlow(LogFontScale.sanitised(stored.logFontScale))
    override val logFontScale = _logFontScale.asStateFlow()

    private val _restoreLastLayout = MutableStateFlow(stored.restoreLastLayout)
    override val restoreLastLayout = _restoreLastLayout.asStateFlow()

    private val _confirmSessionClose = MutableStateFlow(stored.confirmSessionClose)
    override val confirmSessionClose = _confirmSessionClose.asStateFlow()

    private val _confirmExit = MutableStateFlow(stored.confirmExit)
    override val confirmExit = _confirmExit.asStateFlow()

    private val _checkForUpdatesOnStartup = MutableStateFlow(stored.checkForUpdatesOnStartup)
    override val checkForUpdatesOnStartup = _checkForUpdatesOnStartup.asStateFlow()

    private val _notificationDismissSeconds =
        MutableStateFlow(stored.notificationDismissSeconds.coerceAtLeast(0))
    override val notificationDismissSeconds = _notificationDismissSeconds.asStateFlow()

    private val _selectMessageOnly = MutableStateFlow(stored.selectMessageOnly)
    override val selectMessageOnly = _selectMessageOnly.asStateFlow()

    // In-memory only: setCompactView() deliberately never calls update(), so this is the one flow
    // here that does not round-trip through Stored/settings.json.
    private val _isCompactView = MutableStateFlow(false)
    override val isCompactView = _isCompactView.asStateFlow()

    override fun setMemoryCapBytes(bytes: Long) = update { _memoryCapBytes.value = bytes }

    override fun setThemeMode(mode: ThemeMode) = update { _themeMode.value = mode }

    override fun setLogColumns(columns: LogColumns) = update { _logColumns.value = columns }

    override fun setLogFontScale(scale: Float) =
        update { _logFontScale.value = LogFontScale.sanitised(scale) }

    override fun setRestoreLastLayout(restore: Boolean) = update { _restoreLastLayout.value = restore }

    override fun setConfirmSessionClose(confirm: Boolean) = update { _confirmSessionClose.value = confirm }

    override fun setConfirmExit(confirm: Boolean) = update { _confirmExit.value = confirm }

    override fun setCheckForUpdatesOnStartup(check: Boolean) =
        update { _checkForUpdatesOnStartup.value = check }

    override fun setNotificationDismissSeconds(seconds: Int) =
        update { _notificationDismissSeconds.value = seconds.coerceAtLeast(0) }

    override fun setSelectMessageOnly(messageOnly: Boolean) =
        update { _selectMessageOnly.value = messageOnly }

    // No update() call: intentionally never written to settings.json — see isCompactView above.
    override fun setCompactView(compact: Boolean) {
        _isCompactView.value = compact
    }

    private fun update(change: () -> Unit) {
        change()
        val snapshot = Stored.from(
            memoryCapBytes = _memoryCapBytes.value,
            themeMode = _themeMode.value,
            columns = _logColumns.value,
            logFontScale = _logFontScale.value,
            restoreLastLayout = _restoreLastLayout.value,
            confirmSessionClose = _confirmSessionClose.value,
            confirmExit = _confirmExit.value,
            checkForUpdatesOnStartup = _checkForUpdatesOnStartup.value,
            notificationDismissSeconds = _notificationDismissSeconds.value,
            selectMessageOnly = _selectMessageOnly.value,
        )
        appScope.launch(Dispatchers.IO) {
            runCatching {
                Files.createDirectories(file.parent)
                Files.writeString(file, json.encodeToString(snapshot))
            }.onFailure { Log.w(it) { "Could not save settings to $file" } }
        }
    }

    private fun load(): Stored = runCatching {
        if (Files.exists(file)) json.decodeFromString<Stored>(Files.readString(file)) else Stored()
    }.getOrElse {
        Log.w(it) { "Could not read settings from $file; using defaults" }
        Stored()
    }

    /** Flat on purpose: it keeps kotlinx-serialization out of `:domain`. */
    @Serializable
    private data class Stored(
        val memoryCapBytes: Long = SettingsRepository.DEFAULT_MEMORY_CAP_BYTES,
        val themeMode: String? = null,
        /** Superseded by [themeMode]; still read so an older settings file keeps its choice. */
        val isDarkTheme: Boolean = true,
        val showTimestamps: Boolean = true,
        val showLevel: Boolean = true,
        val showTag: Boolean = true,
        val showProcessIds: Boolean = false,
        val softWrapLines: Boolean = false,
        val restoreLastLayout: Boolean = true,
        val lineLayout: String = LogLineLayout.Stacked.name,
        val logFontScale: Float = LogFontScale.Default,
        val confirmSessionClose: Boolean = true,
        val confirmExit: Boolean = true,
        val checkForUpdatesOnStartup: Boolean = true,
        val notificationDismissSeconds: Int = SettingsRepository.DEFAULT_NOTIFICATION_DISMISS_SECONDS,
        val selectMessageOnly: Boolean = false,
    ) {
        fun toThemeMode(): ThemeMode = themeMode
            ?.let { name -> runCatching { ThemeMode.valueOf(name) }.getOrNull() }
            ?: if (isDarkTheme) ThemeMode.Dark else ThemeMode.Light

        fun toLogColumns() = LogColumns(
            timestamp = showTimestamps,
            level = showLevel,
            tag = showTag,
            processIds = showProcessIds,
            softWrap = softWrapLines,
            layout = runCatching { LogLineLayout.valueOf(lineLayout) }.getOrDefault(LogLineLayout.Stacked),
        )

        companion object {
            fun from(
                memoryCapBytes: Long,
                themeMode: ThemeMode,
                columns: LogColumns,
                logFontScale: Float,
                restoreLastLayout: Boolean,
                confirmSessionClose: Boolean,
                confirmExit: Boolean,
                checkForUpdatesOnStartup: Boolean,
                notificationDismissSeconds: Int,
                selectMessageOnly: Boolean,
            ) = Stored(
                memoryCapBytes = memoryCapBytes,
                themeMode = themeMode.name,
                restoreLastLayout = restoreLastLayout,
                confirmSessionClose = confirmSessionClose,
                confirmExit = confirmExit,
                checkForUpdatesOnStartup = checkForUpdatesOnStartup,
                notificationDismissSeconds = notificationDismissSeconds,
                selectMessageOnly = selectMessageOnly,
                showTimestamps = columns.timestamp,
                showLevel = columns.level,
                showTag = columns.tag,
                showProcessIds = columns.processIds,
                softWrapLines = columns.softWrap,
                lineLayout = columns.layout.name,
                logFontScale = logFontScale,
            )
        }
    }
}
