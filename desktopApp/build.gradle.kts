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
            packageVersion = "1.0.0"

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
