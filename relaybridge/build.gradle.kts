plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.koinCompiler)
}

val ktorVersion = "2.3.12"

dependencies {
    implementation(project(":domain"))
    implementation(project(":net"))
    implementation(project(":auth"))
    implementation(project(":adb"))
    implementation("io.ktor:ktor-client-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-cio-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-websockets-jvm:$ktorVersion")
    implementation("org.jmdns:jmdns:3.5.10")
    implementation(libs.kotlinx.serializationJson)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.testJunit)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutinesTest)
    testRuntimeOnly(libs.logback.classic)
}
