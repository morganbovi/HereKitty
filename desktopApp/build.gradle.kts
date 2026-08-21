import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(project(":app"))

    implementation(compose.desktop.currentOs) {
        exclude(group = "org.jetbrains.compose.material")
    }
    implementation(libs.kotlinx.coroutinesSwing)
    implementation(libs.logback.classic)
}

/**
 * Jewel's themed title bar only works on the JetBrains Runtime, and Compose's run task uses this
 * rather than the Kotlin toolchain, so the JBR has to be named explicitly.
 */
val jetbrainsRuntime = javaToolchains.launcherFor {
    languageVersion.set(JavaLanguageVersion.of(25))
    vendor.set(JvmVendorSpec.JETBRAINS)
}

compose.desktop {
    application {
        mainClass = "io.sweatshop.herekitty.MainKt"
        javaHome = jetbrainsRuntime.get().metadata.installationPath.asFile.absolutePath

        // Without this the macOS menu bar and dock read "MainKt", after the main class.
        jvmArgs += listOf("-Xdock:name=HereKitty", "-Dapple.awt.application.name=HereKitty")

        // A `run` task has no bundle to take a dock icon from, so it needs pointing at one.
        jvmArgs += "-Xdock:icon=${project.file("src/main/resources/app-icon.png").absolutePath}"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "HereKitty"
            packageVersion = project.version.toString()

            // jpackage jlinks a minimal runtime, and these three are outside its default set. Without
            // jdk.unsupported the packaged app dies at first paint on Jewel's use of sun.misc.Unsafe —
            // which `run` never shows, because it uses the whole JDK. From `suggestRuntimeModules`.
            modules("java.instrument", "java.naming", "jdk.unsupported")

            // jpackage wants a different container per platform, all built from the same artwork.
            macOS {
                dockName = "HereKitty"
                bundleID = "io.sweatshop.herekitty"
                iconFile.set(project.file("icons/HereKitty.icns"))
            }

            windows {
                iconFile.set(project.file("icons/HereKitty.ico"))
            }

            linux {
                iconFile.set(project.file("src/main/resources/app-icon.png"))
            }
        }
    }
}

/*
 * Both packaging bugs this project has hit — a runtime image missing `jdk.unsupported`, and two
 * copies of kotlinx-coroutines on the launcher's classpath — produced a *successful* build and then
 * died on the first frame. Neither is visible to `./gradlew run`, so they are asserted against the
 * built image instead: cheap locally, and the only thing that makes a CI package job meaningful.
 */
val verifyDistributable by tasks.registering {
    description = "Checks the built app image for the packaging faults that only appear at runtime."

    val imageDirectory = layout.buildDirectory.dir("compose/binaries/main/app")
    outputs.upToDateWhen { false }

    doLast {
        val image = imageDirectory.get().asFile
        require(image.isDirectory) { "No app image at $image — run createDistributable first" }

        val problems = mutableListOf<String>()

        // Gradle cannot dedupe the IntelliJ icon artifacts' forked coroutines against the real one,
        // and the launcher binds to whichever jar it lists first.
        val coroutineJars = image.walkTopDown()
            .filter { it.name.startsWith("kotlinx-coroutines-core") && it.extension == "jar" }
            .map { it.name }
            .toList()
        if (coroutineJars.size != 1) {
            problems += "expected exactly one kotlinx-coroutines-core jar, found ${coroutineJars.size}: $coroutineJars"
        }

        // jpackage jlinks a minimal runtime; anything outside its default set has to be asked for.
        val releaseFile = image.walkTopDown().firstOrNull { it.name == "release" && it.isFile }
        if (releaseFile == null) {
            problems += "no runtime `release` file found, so its module list cannot be checked"
        } else {
            val modules = releaseFile.readLines().firstOrNull { it.startsWith("MODULES=") }.orEmpty()
            listOf("jdk.unsupported", "java.naming", "java.instrument").forEach { module ->
                if (module !in modules) problems += "runtime image is missing the $module module"
            }
        }

        if (problems.isNotEmpty()) {
            error(problems.joinToString(prefix = "The built app image is not distributable:\n  - ", separator = "\n  - "))
        }

        logger.lifecycle("App image checks passed: one coroutines jar, required JDK modules present")
    }
}

tasks.matching { it.name.startsWith("package") && it.name.endsWith("Dmg") }.configureEach {
    finalizedBy(verifyDistributable)
}
