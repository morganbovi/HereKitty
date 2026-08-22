plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.koinCompiler)
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":net"))
    implementation(libs.kotlinx.serializationJson)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.testJunit)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutinesTest)
    testRuntimeOnly(libs.logback.classic)
}

tasks.withType<Test>().configureEach {
    // Opt in to the test that actually calls the GitHub API.
    providers.systemProperty("herekitty.network").orNull?.let { systemProperty("herekitty.network", it) }
}
