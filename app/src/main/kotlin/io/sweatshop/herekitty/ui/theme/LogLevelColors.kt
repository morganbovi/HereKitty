package io.sweatshop.herekitty.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import io.sweatshop.herekitty.domain.features.logs.model.LogLevel
import org.jetbrains.jewel.foundation.theme.JewelTheme

@Composable
@ReadOnlyComposable
fun colorFor(level: LogLevel): Color = if (JewelTheme.isDark) darkColor(level) else lightColor(level)

private fun darkColor(level: LogLevel): Color = when (level) {
    LogLevel.VERBOSE -> Color(0xFF7A7E85)
    LogLevel.DEBUG -> Color(0xFF9DA0A8)
    LogLevel.INFO -> Color(0xFF5FAD65)
    LogLevel.WARN -> Color(0xFFD9A343)
    LogLevel.ERROR -> Color(0xFFE05555)
    LogLevel.ASSERT -> Color(0xFFFF6B6B)
}

private fun lightColor(level: LogLevel): Color = when (level) {
    LogLevel.VERBOSE -> Color(0xFF8C8C8C)
    LogLevel.DEBUG -> Color(0xFF5A5D63)
    LogLevel.INFO -> Color(0xFF2E7D32)
    LogLevel.WARN -> Color(0xFF9A6700)
    LogLevel.ERROR -> Color(0xFFC5221F)
    LogLevel.ASSERT -> Color(0xFFA31515)
}
