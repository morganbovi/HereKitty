package io.sweatshop.herekitty.repository.updates

import io.sweatshop.herekitty.domain.base.Log
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The platform mechanics of replacing an installed app with a downloaded one.
 *
 * All of it is macOS-shaped, because that is the only bundle this project builds. Elsewhere
 * [installedBundle] finds nothing and the caller reports that an update has to be installed by hand,
 * which is honest rather than half-working.
 */
internal object StagedUpdate {

    /**
     * The `.app` this process is running from, or null when it is not running from one at all —
     * which is the case under `gradlew run`, where there is no bundle to replace.
     */
    fun installedBundle(): Path? {
        val launcher = System.getProperty("jpackage.app-path")
            ?: ProcessHandle.current().info().command().orElse(null)
            ?: return null

        // .../HereKitty.app/Contents/MacOS/HereKitty — the bundle is three levels up.
        val bundle = Path.of(launcher).parent?.parent?.parent ?: return null
        return bundle.takeIf { it.name.endsWith(".app") && Files.isDirectory(it) }
    }

    /**
     * Unpacks a downloaded archive and returns the `.app` inside it.
     *
     * `ditto` rather than `java.util.zip`, which does not carry an app bundle's symlinks or permission
     * bits through — the unpacked copy would be subtly broken in ways that only show on launch.
     *
     * Done before the app quits, so a bad archive is a visible failure in a running window rather than
     * an app that has already exited and cannot come back.
     */
    suspend fun unpack(archive: Path, into: Path): Path = withContext(Dispatchers.IO) {
        require(archive.name.endsWith(".zip")) {
            "${archive.name} is not an archive this can unpack; install it by hand"
        }

        if (Files.exists(into)) into.toFile().deleteRecursively()
        Files.createDirectories(into)

        run("ditto", "-x", "-k", archive.toString(), into.toString())

        Files.list(into).use { entries ->
            entries.filter { it.name.endsWith(".app") }.findFirst().orElse(null)
        } ?: error("${archive.name} contained no application")
    }

    /**
     * Hands the swap to a process that outlives this one and returns; the caller then quits.
     *
     * A running application cannot replace its own bundle, so the move has to happen after it exits.
     * The helper is a detached script rather than a second JVM: it waits on this process's id, swaps
     * the directories, and relaunches. It keeps the old bundle until the move succeeds, so a failure
     * halfway leaves the previous version in place rather than nothing at all.
     */
    suspend fun swapAfterExit(installed: Path, staged: Path): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val script = Files.createTempFile("herekitty-update", ".sh")
            Files.writeString(
                script,
                """
                #!/bin/sh
                set -e
                installed="$1"
                staged="$2"
                pid="$3"

                while kill -0 "${'$'}pid" 2>/dev/null; do sleep 0.2; done

                previous="${'$'}installed.previous"
                rm -rf "${'$'}previous"
                mv "${'$'}installed" "${'$'}previous"
                if mv "${'$'}staged" "${'$'}installed"; then
                  rm -rf "${'$'}previous"
                else
                  mv "${'$'}previous" "${'$'}installed"
                fi

                open "${'$'}installed"
                """.trimIndent(),
            )
            script.toFile().setExecutable(true)

            ProcessBuilder(
                "/bin/sh",
                script.toString(),
                installed.toString(),
                staged.toString(),
                ProcessHandle.current().pid().toString(),
            ).start()

            Log.i { "Update helper started; $installed will be replaced once this process exits" }
            true
        }.onFailure { Log.e(it) { "Could not start the update helper" } }.getOrDefault(false)
    }

    private fun run(vararg command: String) {
        val process = ProcessBuilder(*command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText().trim()
        check(process.waitFor() == 0) { "${command.first()} failed: $output" }
    }
}
