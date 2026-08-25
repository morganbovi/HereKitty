package io.sweatshop.herekitty.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.icons.AllIconsKeys

/**
 * Jewel ships the icon *keys*; the SVGs behind them come from the separate
 * `com.jetbrains.intellij.platform:icons` artifact, whose version tracks IntelliJ build numbers.
 * A key that no longer resolves renders as nothing at all, so pin the toolbars down here.
 */
class AppIconsTest {

    private val iconsUsedByTheApp: Map<String, IconKey> = mapOf(
        "General.Add" to AllIconsKeys.General.Add,
        "General.Close" to AllIconsKeys.General.Close,
        "General.CloseSmall" to AllIconsKeys.General.CloseSmall,
        "General.ChevronDown" to AllIconsKeys.General.ChevronDown,
        "General.Settings" to AllIconsKeys.General.Settings,
        "General.Layout" to AllIconsKeys.General.Layout,
        "General.BalloonInformation" to AllIconsKeys.General.BalloonInformation,
        "General.BalloonError" to AllIconsKeys.General.BalloonError,
        "General.Mouse" to AllIconsKeys.General.Mouse,
        "General.History" to AllIconsKeys.General.History,
        "General.Drag" to AllIconsKeys.General.Drag,
        "Nodes.Tag" to AllIconsKeys.Nodes.Tag,
        "Actions.Search" to AllIconsKeys.Actions.Search,
        "Actions.MatchCase" to AllIconsKeys.Actions.MatchCase,
        "Actions.Regex" to AllIconsKeys.Actions.Regex,
        "Actions.Cancel" to AllIconsKeys.Actions.Cancel,
        "Actions.Colors" to AllIconsKeys.Actions.Colors,
        "Actions.GC" to AllIconsKeys.Actions.GC,
        "Actions.Pause" to AllIconsKeys.Actions.Pause,
        "Actions.Resume" to AllIconsKeys.Actions.Resume,
        "Actions.Refresh" to AllIconsKeys.Actions.Refresh,
        "Actions.More" to AllIconsKeys.Actions.More,
        "Actions.ChangeView" to AllIconsKeys.Actions.ChangeView,
        "Actions.Upload" to AllIconsKeys.Actions.Upload,
        "Actions.MenuSaveall" to AllIconsKeys.Actions.MenuSaveall,
        "Actions.MenuOpen" to AllIconsKeys.Actions.MenuOpen,
        "Actions.Checked" to AllIconsKeys.Actions.Checked,
        "Actions.Collapseall" to AllIconsKeys.Actions.Collapseall,
        "General.CollapseComponent" to AllIconsKeys.General.CollapseComponent,
        "General.Delete" to AllIconsKeys.General.Delete,
        "General.BalloonInformation" to AllIconsKeys.General.BalloonInformation,
        "Actions.SplitVertically" to AllIconsKeys.Actions.SplitVertically,
        "Actions.SplitHorizontally" to AllIconsKeys.Actions.SplitHorizontally,
        "RunConfigurations.Scroll_down" to AllIconsKeys.RunConfigurations.Scroll_down,
    )

    @Test
    fun `every icon the app uses resolves to a bundled resource`() {
        val missing = iconsUsedByTheApp.filterValues { key -> resourceFor(key) == null }

        assertTrue(
            missing.isEmpty(),
            "No SVG on the classpath for: " + missing.entries.joinToString { "${it.key} -> ${it.value.path(true)}" },
        )
    }

    private fun resourceFor(key: IconKey): String? {
        val loader = key.iconClass.classLoader
        return listOf(key.path(isNewUi = true), key.path(isNewUi = false))
            .flatMap { path -> listOf(path, path.replace(".svg", "_dark.svg")) }
            .firstOrNull { loader.getResource(it) != null }
    }
}
