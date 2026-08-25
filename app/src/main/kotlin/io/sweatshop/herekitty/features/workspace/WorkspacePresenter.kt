package io.sweatshop.herekitty.features.workspace

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import io.sweatshop.herekitty.domain.base.launchCoroutine
import io.sweatshop.herekitty.domain.features.devices.repository.DeviceRepository
import io.sweatshop.herekitty.domain.features.lifecycle.repository.AppLifecycleRepository
import io.sweatshop.herekitty.domain.features.logs.repository.LastSessionRepository
import io.sweatshop.herekitty.domain.features.logs.repository.LogSessionRepository
import io.sweatshop.herekitty.domain.features.views.model.ViewConfig
import io.sweatshop.herekitty.domain.features.settings.repository.SettingsRepository
import io.sweatshop.herekitty.domain.features.views.repository.ViewConfigRepository
import io.sweatshop.herekitty.domain.features.workspace.model.StoredLayout
import io.sweatshop.herekitty.domain.features.workspace.repository.WorkspaceLayoutRepository
import io.sweatshop.herekitty.features.workspace.WorkspaceUiModel.Event.OnChildrenReordered
import io.sweatshop.herekitty.features.workspace.WorkspaceUiModel.Event.OnDeviceChosen
import io.sweatshop.herekitty.features.workspace.WorkspaceUiModel.Event.OnNewTabClicked
import io.sweatshop.herekitty.features.workspace.WorkspaceUiModel.Event.OnNoticeDismissed
import io.sweatshop.herekitty.features.workspace.WorkspaceUiModel.Event.OnNoticeRaised
import io.sweatshop.herekitty.features.workspace.WorkspaceUiModel.Event.OnRecordingChosen
import io.sweatshop.herekitty.features.workspace.WorkspaceUiModel.Event.OnSlotClosed
import io.sweatshop.herekitty.features.workspace.WorkspaceUiModel.Event.OnSplitRequested
import io.sweatshop.herekitty.features.workspace.WorkspaceUiModel.Event.OnSwitchSourceRequested
import io.sweatshop.herekitty.features.workspace.WorkspaceUiModel.Event.OnTabClosed
import io.sweatshop.herekitty.features.workspace.WorkspaceUiModel.Event.OnTabSelected
import io.sweatshop.herekitty.features.workspace.WorkspaceUiModel.Event.OnViewChanged
import io.sweatshop.herekitty.features.workspace.WorkspaceUiModel.Event.OnViewDeleted
import io.sweatshop.herekitty.features.workspace.WorkspaceUiModel.Event.OnViewExported
import io.sweatshop.herekitty.features.workspace.WorkspaceUiModel.Event.OnViewImported
import io.sweatshop.herekitty.features.workspace.WorkspaceUiModel.Event.OnViewSaved
import io.sweatshop.herekitty.ui.notification.AppNotification
import io.sweatshop.herekitty.ui.notification.NotificationSeverity
import io.sweatshop.herekitty.ui.notification.plusCapped
import io.sweatshop.herekitty.ui.presenter.EventHandler
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.annotation.Factory

/**
 * Owns every bit of layout state: the tabs, each tab's tree, and each slot's view config. Keeping it
 * in one place is what makes applying a saved view, switching a session's source, and reordering
 * panes all the same kind of operation.
 */
@Factory
class WorkspacePresenter(
    private val sessionRepository: LogSessionRepository,
    private val viewConfigRepository: ViewConfigRepository,
    private val layoutRepository: WorkspaceLayoutRepository,
    private val deviceRepository: DeviceRepository,
    private val settingsRepository: SettingsRepository,
    private val lastSessionRepository: LastSessionRepository,
    private val lifecycleRepository: AppLifecycleRepository,
) {

    @Composable
    fun present(): WorkspaceUiModel {
        val scope = rememberCoroutineScope()
        val savedViews by viewConfigRepository.savedViews.collectAsState()

        // Read once: flipping the setting should change the next launch, not this one.
        val restored = remember {
            if (settingsRepository.restoreLastLayout.value) layoutRepository.load() else StoredLayout.Empty
        }
        var nextId by remember { mutableStateOf(0L) }

        fun newId(): Long = nextId.also { nextId++ }

        var tabs by remember {
            mutableStateOf(
                restored.toWorkspaceTabs(::newId).ifEmpty {
                    listOf(WorkspaceTab(TabId(newId()), WorkspaceNode.Slot(NodeId(newId()))))
                },
            )
        }
        var activeTabId by remember { mutableStateOf(tabs.first().id) }
        var notifications by remember { mutableStateOf<List<AppNotification>>(emptyList()) }
        var nextNotificationId by remember { mutableStateOf(0L) }

        fun raise(message: String, severity: NotificationSeverity = NotificationSeverity.Info) {
            notifications = notifications.plusCapped(
                AppNotification(nextNotificationId++, message, severity),
            )
        }

        fun raiseFailure(message: String?, fallback: String) {
            raise(message ?: fallback, NotificationSeverity.Error)
        }
        var hasReattached by remember { mutableStateOf(restored.isEmpty) }

        // Read once, and reset in the same call: if this run never reaches markCleanExit, the next
        // launch sees false without anyone having to remember to leave a "still running" mark behind.
        val wasCleanExit = remember { lifecycleRepository.consumeLastExitWasClean() }

        // The layout comes back before adb has said what is plugged in, so reattaching waits for the
        // first device list rather than guessing.
        LaunchedEffect(restored) {
            if (hasReattached) return@LaunchedEffect
            val wanted = restored.serialsByPosition()
            if (wanted.none { it != null }) {
                hasReattached = true
                return@LaunchedEffect
            }

            val available = withTimeoutOrNull(REATTACH_TIMEOUT_MILLIS) {
                deviceRepository.devices.first { devices -> devices.any { it.state.canStreamLogs } }
            }
            hasReattached = true

            val bySerial = available.orEmpty().filter { it.state.canStreamLogs }.associateBy { it.serial }

            var slotIndex = 0
            val missing = mutableListOf<Pair<NodeId, String>>()
            tabs = tabs.map { tab ->
                tab.copy(
                    root = tab.root.mapSlots { slot ->
                        val serial = wanted.getOrNull(slotIndex++)
                        val device = serial?.let { bySerial[it] }
                        when {
                            device != null -> slot.copy(session = sessionRepository.open(device))
                            serial != null -> { missing += slot.id to serial; slot }
                            else -> slot
                        }
                    },
                )
            }
            // Nothing crashed, so reattaching is as far as this goes: the slot lands on the source
            // picker, which finds the cache back on its own — offered as a choice, not forced here.
            if (missing.isEmpty() || wasCleanExit) return@LaunchedEffect

            // The previous run never reached its own quit path, so this is a crash recovery, not a
            // choice the user gets to sit through — resume silently, the way a browser would.
            var resumedAny = false
            missing.forEach { (slotId, serial) ->
                val info = lastSessionRepository.find(serial) ?: return@forEach
                sessionRepository.openRecording(info.path).onSuccess { session ->
                    resumedAny = true
                    tabs = tabs.map { it.copy(root = it.root.updateSlot(slotId) { s -> s.copy(session = session) }) }
                }
            }
            if (resumedAny) raise("Reopened your last session after HereKitty quit unexpectedly")
        }

        // Persist whatever the layout currently is, a beat after it settles.
        LaunchedEffect(tabs) {
            delay(LAYOUT_SAVE_DELAY_MILLIS)
            layoutRepository.save(tabs.toStoredLayout())
        }

        fun updateActiveRoot(change: (WorkspaceNode) -> WorkspaceNode?) {
            tabs = tabs.map { tab ->
                if (tab.id != activeTabId) tab else tab.copy(root = change(tab.root) ?: WorkspaceNode.Slot(NodeId(newId())))
            }
        }

        fun closeSessionsUnder(node: WorkspaceNode) {
            node.slots().forEach { slot -> slot.session?.let { sessionRepository.close(it.id) } }
        }

        return WorkspaceUiModel(
            tabs = tabs,
            activeTabId = activeTabId,
            savedViews = savedViews,
            notifications = notifications,
            eventHandler = EventHandler { event ->
                when (event) {
                    OnNewTabClicked -> {
                        val tab = WorkspaceTab(TabId(newId()), WorkspaceNode.Slot(NodeId(newId())))
                        tabs = tabs + tab
                        activeTabId = tab.id
                    }

                    is OnTabSelected -> activeTabId = event.tabId

                    is OnTabClosed -> {
                        val closing = tabs.firstOrNull { it.id == event.tabId } ?: return@EventHandler
                        closeSessionsUnder(closing.root)

                        // Closing the last tab is how a blank slate is asked for, so it comes back
                        // empty rather than being refused: no session, and no restored view riding
                        // along into whatever is opened next.
                        tabs = tabs.filterNot { it.id == event.tabId }.ifEmpty {
                            listOf(WorkspaceTab(TabId(newId()), WorkspaceNode.Slot(NodeId(newId()))))
                        }
                        if (activeTabId == event.tabId) {
                            activeTabId = tabs.last().id
                        }
                    }

                    // Also the path for switching a live session to another device: the old capture
                    // is closed, so the new one starts from an empty buffer.
                    is OnDeviceChosen -> updateActiveRoot { root ->
                        root.updateSlot(event.slotId) { slot ->
                            slot.session?.let { sessionRepository.close(it.id) }
                            slot.copy(session = sessionRepository.open(event.device))
                        }
                    }

                    is OnRecordingChosen -> scope.launchCoroutine(
                        onError = { raiseFailure(it.message, "Could not open that recording") },
                    ) {
                        // A bundle carries the view it was read through; a bare recording does not,
                        // so the slot keeps whatever view it already had.
                        val bundledView = viewConfigRepository.importFrom(event.path).getOrNull()

                        sessionRepository.openRecording(event.path)
                            .onSuccess { session ->
                                updateActiveRoot { root ->
                                    root.updateSlot(event.slotId) { slot ->
                                        slot.session?.let { sessionRepository.close(it.id) }
                                        slot.copy(session = session, view = bundledView ?: slot.view)
                                    }
                                }
                                bundledView?.let { raise("Opened with the view \"${it.name}\"") }
                            }
                            .onFailure { raiseFailure(it.message, "Could not open that recording") }
                    }

                    is OnSwitchSourceRequested -> updateActiveRoot { root ->
                        root.updateSlot(event.slotId) { slot ->
                            slot.session?.let { sessionRepository.close(it.id) }
                            slot.copy(session = null)
                        }
                    }

                    is OnSplitRequested -> updateActiveRoot { root ->
                        root.splitSlot(
                            slotId = event.slotId,
                            orientation = event.orientation,
                            newSlot = WorkspaceNode.Slot(NodeId(newId())),
                            nextId = { NodeId(newId()) },
                        )
                    }

                    is OnSlotClosed -> updateActiveRoot { root ->
                        root.slots().firstOrNull { it.id == event.slotId }?.session
                            ?.let { sessionRepository.close(it.id) }
                        root.withoutSlot(event.slotId)
                    }

                    is OnChildrenReordered -> updateActiveRoot { root ->
                        root.withReorderedChildren(event.splitId, event.from, event.to)
                    }

                    is OnViewChanged -> updateActiveRoot { root ->
                        root.updateSlot(event.slotId) { it.copy(view = event.view) }
                    }

                    is OnViewSaved -> {
                        viewConfigRepository.save(event.view)
                        raise("Saved the view \"${event.view.name}\"")
                    }

                    is OnViewDeleted -> viewConfigRepository.delete(event.name)

                    is OnViewExported -> scope.launchCoroutine(
                        onError = { raiseFailure(it.message, "Could not export that view") },
                    ) {
                        viewConfigRepository.exportTo(event.view, event.path)
                            .onSuccess { raise("Exported to ${it.fileName}") }
                            .onFailure { raiseFailure(it.message, "Could not export that view") }
                    }

                    is OnViewImported -> scope.launchCoroutine(
                        onError = { raiseFailure(it.message, "Could not read that view") },
                    ) {
                        viewConfigRepository.importFrom(event.path)
                            .onSuccess { imported ->
                                updateActiveRoot { root ->
                                    root.updateSlot(event.slotId) { it.copy(view = imported) }
                                }
                                raise("Applied the view \"${imported.name}\"")
                            }
                            .onFailure { raiseFailure(it.message, "Could not read that view") }
                    }

                    is OnNoticeRaised -> raise(event.message, event.severity)

                    is OnNoticeDismissed -> notifications = notifications.filterNot { it.id == event.id }
                }
            },
        )
    }

    private companion object {
        const val REATTACH_TIMEOUT_MILLIS = 5_000L
        const val LAYOUT_SAVE_DELAY_MILLIS = 600L
    }
}
