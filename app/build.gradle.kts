plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.koinCompiler)
}

kotlin {
    compilerOptions {
        // Jewel builds on these, so opting in per call site would mean annotating most of the UI.
        optIn.addAll(
            "androidx.compose.foundation.ExperimentalFoundationApi",
            "androidx.compose.ui.ExperimentalComposeUiApi",
            "org.jetbrains.jewel.foundation.ExperimentalJewelApi",
        )
    }
}

dependencies {
    api(project(":domain"))
    implementation(project(":injector"))
    implementation(project(":net"))

    api(libs.compose.runtime)
    api(libs.compose.foundation)
    api(libs.compose.ui)
    api(libs.jewel.intUiStandalone)
    api(libs.jewel.intUiDecoratedWindow)

    // Resources only: the SVGs behind AllIconsKeys. Jewel ships the keys, not the images.
    runtimeOnly(libs.intellij.icons)

    api(libs.koin.compose)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.testJunit)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutinesTest)
    testRuntimeOnly(libs.logback.classic)
}
