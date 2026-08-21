package io.sweatshop.herekitty

import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import javax.imageio.ImageIO

/**
 * The window's icon, read off the classpath.
 *
 * Decoded here rather than through `painterResource`, which is deprecated in favour of a resources
 * library this module needs for nothing else.
 */
internal object AppIcon {
    val painter: Painter by lazy {
        val stream = checkNotNull(javaClass.getResourceAsStream("/$RESOURCE_NAME")) {
            "$RESOURCE_NAME is missing from the classpath"
        }
        BitmapPainter(stream.use { ImageIO.read(it) }.toComposeImageBitmap())
    }

    const val RESOURCE_NAME = "app-icon.png"
}
