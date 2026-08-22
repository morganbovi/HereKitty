plugins {
    alias(libs.plugins.kotlinJvm)
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":adb"))
    implementation(project(":repository"))
    implementation(project(":updates"))
}
