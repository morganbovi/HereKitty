package io.sweatshop.herekitty.ui.files

import java.awt.EventQueue
import java.awt.FileDialog
import java.awt.Frame
import java.nio.file.Path

/**
 * Native open and save dialogs.
 *
 * `FileDialog` is modal and must run on the AWT event thread. Compose Desktop already dispatches
 * click handlers there, but callers are not required to know that, so this hops over when needed.
 */
object FileDialogs {

    fun openFile(title: String, extension: String): Path? = onEventThread {
        dialog(title, FileDialog.LOAD, listOf(extension), suggestedName = null)
    }

    /** A recording is either a bare log or a bundle carrying its view alongside it. */
    fun openRecording(): Path? = onEventThread {
        dialog(
            title = "Open a HereKitty recording or bundle",
            mode = FileDialog.LOAD,
            extensions = listOf("hklog.gz", "hkbundle"),
            suggestedName = null,
        )
    }

    fun saveFile(title: String, suggestedName: String, extension: String): Path? = onEventThread {
        dialog(title, FileDialog.SAVE, listOf(extension), suggestedName)
    }

    private fun dialog(title: String, mode: Int, extensions: List<String>, suggestedName: String?): Path? {
        val primary = extensions.first()
        val chooser = FileDialog(null as Frame?, title, mode).apply {
            setFilenameFilter { _, name -> extensions.any { name.endsWith(".$it", ignoreCase = true) } }
            suggestedName?.let { file = "$it.$primary" }
            isVisible = true
        }

        val directory = chooser.directory ?: return null
        val name = chooser.file ?: return null

        val chosen = Path.of(directory, name)
        val alreadySuffixed = extensions.any { name.endsWith(".$it", ignoreCase = true) }
        return if (mode == FileDialog.SAVE && !alreadySuffixed) {
            chosen.resolveSibling("$name.$primary")
        } else {
            chosen
        }
    }

    private fun <T> onEventThread(block: () -> T): T? {
        if (EventQueue.isDispatchThread()) return block()

        var result: T? = null
        EventQueue.invokeAndWait { result = block() }
        return result
    }
}
