package io.sweatshop.herekitty.adb

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue

/**
 * Downloads the real archive from Google, so it is opt-in:
 *
 * ```
 * ./gradlew :adb:test -Dherekitty.network=true
 * ```
 */
class PlatformToolsInstallerTest {

    @Test
    fun `installs a working adb into the directory it is given`() {
        assumeTrue(System.getProperty("herekitty.network") == "true")

        val root = Files.createTempDirectory("herekitty-install-test").toFile()
        val seen = mutableListOf<Float?>()

        val adb = runBlocking { PlatformToolsInstaller().installInto(root) { seen += it } }

        assertTrue(adb.isFile, "adb was not extracted")
        assertTrue(adb.canExecute(), "adb was extracted without its executable bit")
        assertTrue(seen.any { it != null }, "no download progress was ever reported")

        val version = ProcessBuilder(adb.absolutePath, "version").redirectErrorStream(true).start()
        val output = version.inputStream.bufferedReader().readText()
        version.waitFor()
        assertTrue("Android Debug Bridge" in output, "adb did not run: $output")

        root.deleteRecursively()
    }
}
