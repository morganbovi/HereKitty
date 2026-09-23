pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
    // The Koin compiler plugin's Gradle marker artifact is unpublished — map the plugin id to
    // its actual artifact, same fix HereKitty's own settings.gradle.kts uses.
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "io.insert-koin.compiler.plugin") {
                useModule("io.insert-koin:koin-compiler-gradle-plugin:${requested.version}")
            }
        }
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "HereKittyMobile"
include(":app")
include(":domain")
include(":auth")
include(":sources")
include(":injector")
