package io.sweatshop.herekitty.features.workspace

import io.sweatshop.herekitty.domain.features.devices.model.AdbDevice
import io.sweatshop.herekitty.domain.features.views.model.ViewConfig
import io.sweatshop.herekitty.ui.notification.AppNotification
import io.sweatshop.herekitty.ui.notification.NotificationSeverity
import io.sweatshop.herekitty.ui.presenter.EventHandler
import io.sweatshop.herekitty.ui.split.SplitOrientation
import java.nio.file.Path

data class WorkspaceUiModel(
    val tabs: List<WorkspaceTab>,
    val activeTabId: TabId,
    val savedViews: List<ViewConfig>,
    val notifications: List<AppNotification>,
    val eventHandler: EventHandler<Event>,
) {
    val activeRoot: WorkspaceNode? get() = tabs.firstOrNull { it.id == activeTabId }?.root

    /** Index-aligned with [tabs]. Numbering depends on the whole list, so it cannot live on a tab. */
    val tabTitles: List<String> get() = disambiguateTitles(tabs.map { it.baseTitle })

    val canCloseSlots: Boolean get() = activeRoot?.hasMultipleSlots() == true

    val canCloseTabs: Boolean get() = tabs.size > 1 || tabs.any { it.hasContent }

    sealed interface Event {
        data object OnNewTabClicked : Event

        data class OnTabSelected(val tabId: TabId) : Event

        data class OnTabClosed(val tabId: TabId) : Event

        data class OnDeviceChosen(val slotId: NodeId, val device: AdbDevice) : Event

        data class OnRecordingChosen(val slotId: NodeId, val path: Path) : Event

        data class OnSwitchSourceRequested(val slotId: NodeId) : Event

        data class OnSplitRequested(val slotId: NodeId, val orientation: SplitOrientation) : Event

        data class OnSlotClosed(val slotId: NodeId) : Event

        data class OnChildrenReordered(val splitId: NodeId, val from: Int, val to: Int) : Event

        data class OnViewChanged(val slotId: NodeId, val view: ViewConfig) : Event

        data class OnViewSaved(val view: ViewConfig) : Event

        data class OnViewDeleted(val name: String) : Event

        data class OnViewExported(val view: ViewConfig, val path: Path) : Event

        data class OnViewImported(val slotId: NodeId, val path: Path) : Event

        data class OnNoticeRaised(
            val message: String,
            val severity: NotificationSeverity = NotificationSeverity.Info,
        ) : Event

        data class OnNoticeDismissed(val id: Long) : Event
    }
}
