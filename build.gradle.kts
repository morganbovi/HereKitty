plugins {
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.koinCompiler) apply false
}

subprojects {
    pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
        extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
            jvmToolchain(25)
        }
    }

    /*
     * The IntelliJ icon artifacts pull in a forked kotlinx-coroutines under a *different group*, so
     * Gradle cannot recognise it as the same module and keeps both. Two copies of kotlinx.coroutines
     * on a packaged classpath resolve to whichever jar the launcher lists first, which is not the one
     * anything was compiled against — `packageDmg` produced an app that died on a NoSuchMethodError
     * while `run`, whose classpath happens to be ordered the other way, was fine.
     */
    configurations.configureEach {
        exclude(group = "org.jetbrains.intellij.deps.kotlinx")
    }
}
