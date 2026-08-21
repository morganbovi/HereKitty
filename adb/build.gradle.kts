plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.koinCompiler)
}

dependencies {
    implementation(project(":domain"))

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.testJunit)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutinesTest)
    testRuntimeOnly(libs.logback.classic)
}

tasks.withType<Test>().configureEach {
    // Opt in to PlatformToolsInstallerTest, which downloads the real archive from Google.
    providers.systemProperty("herekitty.network").orNull?.let { systemProperty("herekitty.network", it) }
}
