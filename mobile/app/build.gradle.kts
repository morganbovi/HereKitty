import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
    alias(libs.plugins.koin.compiler)
}

// `relayUrl` in local.properties can be a Cloudflare `wss://` endpoint. The legacy host/port
// pair remains a LAN-development fallback so existing setups do not need to change at once.
val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
val relayUrl = localProperties.getProperty("relayUrl")?.trim()?.removeSuffix("/")
    ?.takeIf { it.isNotBlank() }
    ?: localProperties.getProperty("relayHost")?.trim()?.takeIf { it.isNotBlank() }?.let { host ->
        "ws://$host:${localProperties.getProperty("relayPort", "7050")}" 
    }.orEmpty()

android {
    namespace = "io.sweatshop.herekitty.mobile"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.sweatshop.herekitty.mobile"
        // Wireless debugging (the thing this spike is testing) needs API 30+.
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "0.1-spike"

        buildConfigField("String", "RELAY_URL", "\"$relayUrl\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// The Koin compiler plugin's compile-time safety check (1.1.0's full-graph validation) can't see
// bindings defined in other Android library modules (:auth, :sources) from this module's own
// annotation-processing pass, and flags genuinely-working cross-module injections (LoginPresenter's
// AuthRepository, RelayShareService's DeviceRepository -- both provided by other modules and
// resolved fine at runtime) as missing. Desktop HereKitty's plain kotlinJvm modules don't hit this;
// it's specific to this module topology. Disabled rather than worked around per-call-site.
koinCompiler {
    compileSafety = false
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":injector"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.credentials)
    implementation(libs.credentials.play)
    implementation(libs.googleid)
    implementation(libs.firebase.messaging)
    implementation(libs.koin.core)
    implementation(libs.koin.annotations)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
