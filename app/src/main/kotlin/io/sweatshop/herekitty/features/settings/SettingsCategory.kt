package io.sweatshop.herekitty.features.settings

import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.icons.AllIconsKeys

enum class SettingsCategory(val title: String, val icon: IconKey) {
    Appearance("Appearance", AllIconsKeys.Actions.Colors),
    LogDisplay("Log display", AllIconsKeys.General.Layout),
    Recording("Recording", AllIconsKeys.Actions.GC),
    Prompts("Prompts", AllIconsKeys.General.BalloonInformation),
}
