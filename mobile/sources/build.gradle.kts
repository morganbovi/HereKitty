plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.koin.compiler)
}

android {
    namespace = "io.sweatshop.herekitty.mobile.sources"
    compileSdk = 36
    defaultConfig { minSdk = 30 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(project(":domain"))
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.database)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.koin.core)
    implementation(libs.koin.annotations)
}
