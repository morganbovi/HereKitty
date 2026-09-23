plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.koinCompiler)
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":net"))
    implementation(project(":auth"))
    implementation(libs.kotlinx.serializationJson)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.testJunit)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutinesTest)
    testRuntimeOnly(libs.logback.classic)
}
