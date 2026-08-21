package io.sweatshop.herekitty.adb

import io.sweatshop.herekitty.domain.base.Log
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipInputStream
import kotlin.io.path.extension
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single

/**
 * Fetches Google's platform-tools into the app's own directory.
 *
 * Downloaded rather than shipped: the Android SDK's terms do not grant redistribution, and a bundled
 * copy would be three platforms' worth of binaries going stale in the repository. The URL is the one
 * Google keeps pointed at the current release, so an install is always up to date.
 */
@Single
class PlatformToolsInstaller {

    val installedAdb: File get() = managedAdb

    suspend fun install(onProgress: (Float?) -> Unit): File =
        installInto(managedPlatformToolsRoot, onProgress)

    /** Takes the root as an argument so a test can exercise the real download without touching `~`. */
    internal suspend fun installInto(root: File, onProgress: (Float?) -> Unit): File =
        withContext(Dispatchers.IO) {
            val archive = Files.createTempFile("herekitty-platform-tools", ".zip")
            try {
                download(archive, onProgress)
                onProgress(null)
                extractInto(archive, root.toPath())
            } finally {
                Files.deleteIfExists(archive)
            }

            File(root, "platform-tools/$adbExecutableName").also {
                check(it.isFile) { "The platform-tools archive did not contain ${it.name}" }
                Log.i { "Installed adb at $it" }
            }
        }

    private fun download(target: Path, onProgress: (Float?) -> Unit) {
        val connection = (URI(downloadUrl).toURL().openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = CONNECT_TIMEOUT_MILLIS
            readTimeout = READ_TIMEOUT_MILLIS
        }

        try {
            check(connection.responseCode == HttpURLConnection.HTTP_OK) {
                "$downloadUrl answered ${connection.responseCode}"
            }

            val expected = connection.contentLengthLong.takeIf { it > 0 }
            connection.inputStream.use { source ->
                Files.newOutputStream(target).use { sink ->
                    val buffer = ByteArray(BUFFER_BYTES)
                    var received = 0L
                    while (true) {
                        val read = source.read(buffer)
                        if (read < 0) break
                        sink.write(buffer, 0, read)
                        received += read
                        onProgress(expected?.let { received.toFloat() / it })
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun extractInto(archive: Path, target: Path) {
        Files.createDirectories(target)

        ZipInputStream(Files.newInputStream(archive).buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val destination = target.resolve(entry.name).normalize()

                // An archive entry that names its way out of the target directory is not one to trust.
                require(destination.startsWith(target)) { "Refusing archive entry ${entry.name}" }

                if (entry.isDirectory) {
                    Files.createDirectories(destination)
                    continue
                }

                Files.createDirectories(destination.parent)
                Files.newOutputStream(destination).use { zip.copyTo(it) }

                // ZipInputStream drops the unix mode, so the bit has to be put back. The tools are the
                // entries with no extension; the licence text and the DLLs beside them need nothing.
                if (destination.extension.isEmpty() || destination.extension == "exe") {
                    destination.toFile().setExecutable(true)
                }
            }
        }
    }

    private val downloadUrl: String
        get() = "https://dl.google.com/android/repository/platform-tools-latest-$platform.zip"

    private val platform: String
        get() {
            val os = System.getProperty("os.name").orEmpty().lowercase()
            return when {
                os.startsWith("mac") || os.startsWith("darwin") -> "darwin"
                os.startsWith("windows") -> "windows"
                else -> "linux"
            }
        }

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 15_000
        const val READ_TIMEOUT_MILLIS = 30_000
        const val BUFFER_BYTES = 1 shl 16
    }
}
