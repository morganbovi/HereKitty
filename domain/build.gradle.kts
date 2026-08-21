plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.koinCompiler)
}

dependencies {
    api(libs.kotlinx.coroutinesCore)
    api(libs.koin.core)
    api(libs.koin.annotations)
    api(libs.kotlinLogging)
    api(libs.slf4j.api)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.testJunit)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutinesTest)
}
