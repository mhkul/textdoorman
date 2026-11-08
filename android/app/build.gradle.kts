import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.textdoorman.android"
    // Compose 2026.09 needs API 37.2 to compile against; runtime behavior follows targetSdk.
    compileSdk = 37
    compileSdkMinor = 2

    defaultConfig {
        applicationId = "com.textdoorman.android"
        minSdk = 29
        targetSdk = 36
        versionCode = 3
        versionName = "0.3"
    }

    buildFeatures {
        compose = true
    }

    // Upload key for Play. Lives OUTSIDE the project in keystore.properties (never committed):
    //   storeFile=C:/path/to/upload-key.jks  storePassword=...  keyAlias=upload  keyPassword=...
    val keyProps = rootProject.file("keystore.properties")
    if (keyProps.exists()) {
        val p = Properties().apply { keyProps.inputStream().use { load(it) } }
        signingConfigs.create("upload") {
            storeFile = file(p.getProperty("storeFile"))
            storePassword = p.getProperty("storePassword")
            keyAlias = p.getProperty("keyAlias")
            keyPassword = p.getProperty("keyPassword")
        }
    }

    buildTypes {
        // debug = the developer's own phone: personal model (src/debug/assets).
        // release = Play Store: generic model with no personal data (src/release/assets).
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            if (keyProps.exists()) signingConfig = signingConfigs.getByName("upload")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    // Real org.json for JVM unit tests (Android's copy is stubbed off-device).
    testImplementation("org.json:json:20260814")
}
