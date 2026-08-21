plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.koinCompiler)
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":adb"))
    implementation(libs.kotlinx.serializationJson)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.testJunit)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutinesTest)
    testRuntimeOnly(libs.logback.classic)
}

tasks.withType<Test>().configureEach {
    // Opt in to LiveAdbPipelineTest, which needs a device attached.
    providers.systemProperty("herekitty.liveAdb").orNull?.let { systemProperty("herekitty.liveAdb", it) }
}
