package io.sweatshop.herekitty.adb

import io.sweatshop.herekitty.domain.base.Log
import java.io.File
import org.koin.core.annotation.Single

@Single
class AdbBinaryLocator {
    @Volatile
    private var found: File? = null

    /**
     * A miss is deliberately not remembered. adb can appear *after* the app starts — which is exactly
     * what the in-app install does — and a cached null would keep it invisible for the rest of the
     * session.
     */
    fun locate(): File? {
        found?.takeIf { it.isUsable }?.let { return it }

        return candidates().firstOrNull { it.isUsable }
            .also { located ->
                found = located
                if (located == null) Log.w { "No adb binary found; searched ${candidates().joinToString()}" }
            }
    }

    /**
     * Ordered so that an adb already on the machine always wins. Ours is the last resort: someone with
     * a working SDK should keep using the binary their other tooling uses, and the copy we downloaded
     * only exists because nothing was found in the first place.
     */
    internal fun candidates(): List<File> = buildList {
        System.getenv("HEREKITTY_ADB")?.let { add(File(it)) }

        listOfNotNull(System.getenv("ANDROID_HOME"), System.getenv("ANDROID_SDK_ROOT")).forEach {
            add(File(it, "platform-tools/$adbExecutableName"))
        }
        val home = System.getProperty("user.home")
        add(File(home, "Library/Android/sdk/platform-tools/$adbExecutableName"))
        add(File(home, "Android/Sdk/platform-tools/$adbExecutableName"))
        System.getenv("LOCALAPPDATA")?.let { add(File(it, "Android\\Sdk\\platform-tools\\$adbExecutableName")) }
        System.getenv("PATH")?.split(File.pathSeparator)?.forEach { add(File(it, adbExecutableName)) }
        add(File("/opt/homebrew/bin/$adbExecutableName"))
        add(File("/usr/local/bin/$adbExecutableName"))

        add(managedAdb)
    }

    private val File.isUsable: Boolean get() = isFile && canExecute()
}
