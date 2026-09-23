plugins {
    alias(libs.plugins.kotlinJvm)
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":adb"))
    implementation(project(":auth"))
    implementation(project(":relaydevices"))
    implementation(project(":relaybridge"))
    implementation(project(":repository"))
    implementation(project(":updates"))
}
