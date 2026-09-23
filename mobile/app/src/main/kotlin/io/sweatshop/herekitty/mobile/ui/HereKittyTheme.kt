package io.sweatshop.herekitty.mobile.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val HereKittyColors = darkColorScheme(
    primary = Color(0xFF8AB4F8),
    onPrimary = Color(0xFF0B1F33),
    primaryContainer = Color(0xFF163A5F),
    onPrimaryContainer = Color(0xFFD7E8FF),
    secondary = Color(0xFFA8C7FA),
    tertiary = Color(0xFF79E2C5),
    background = Color(0xFF1E1F22),
    onBackground = Color(0xFFF1F3F4),
    surface = Color(0xFF282A2E),
    surfaceVariant = Color(0xFF34363B),
    onSurface = Color(0xFFF1F3F4),
    onSurfaceVariant = Color(0xFFC4C7C5),
    outline = Color(0xFF8E918F),
    error = Color(0xFFFFB4AB),
)

@Composable
fun HereKittyTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = HereKittyColors, content = content)
}
