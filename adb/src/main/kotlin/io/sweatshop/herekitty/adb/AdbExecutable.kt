package io.sweatshop.herekitty.adb

import java.io.File

internal val isWindows: Boolean
    get() = System.getProperty("os.name").orEmpty().startsWith("Windows")

internal val adbExecutableName: String
    get() = if (isWindows) "adb.exe" else "adb"

/** Where an in-app install puts the tools, beside the app's other state. Searched last. */
internal val managedPlatformToolsRoot: File
    get() = File(System.getProperty("user.home"), ".herekitty")

internal val managedAdb: File
    get() = File(managedPlatformToolsRoot, "platform-tools/$adbExecutableName")
