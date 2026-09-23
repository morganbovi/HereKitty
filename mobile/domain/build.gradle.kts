plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.koin.compiler)
}

android {
    namespace = "io.sweatshop.herekitty.mobile.domain"
    compileSdk = 36
    defaultConfig { minSdk = 30 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    api(libs.kotlinx.coroutines.android)
    api(libs.koin.core)
    api(libs.koin.annotations)
}
