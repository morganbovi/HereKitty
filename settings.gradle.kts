rootProject.name = "HereKitty"

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "io.insert-koin.compiler.plugin") {
                useModule("io.insert-koin:koin-compiler-gradle-plugin:${requested.version}")
            }
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // The IntelliJ icon SVGs that AllIconsKeys points at are only published here.
        maven("https://cache-redirector.jetbrains.com/intellij-repository/releases") {
            content { includeGroup("com.jetbrains.intellij.platform") }
        }
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include(":desktopApp")
include(":app")
include(":domain")
include(":adb")
include(":auth")
include(":relaydevices")
include(":relaybridge")
include(":repository")
include(":net")
include(":updates")
include(":injector")
