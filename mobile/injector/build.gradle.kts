plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "io.sweatshop.herekitty.mobile.injector"
    compileSdk = 36
    defaultConfig { minSdk = 30 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":auth"))
    implementation(project(":sources"))
}
