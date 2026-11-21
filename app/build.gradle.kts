plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Telegram API keys come from GitHub Secrets (TG_API_ID / TG_API_HASH).
// If they are missing, the app asks for them on first launch.
val tgApiId = System.getenv("TG_API_ID")?.trim()?.toIntOrNull() ?: 0
val tgApiHash = System.getenv("TG_API_HASH")?.trim() ?: ""
val buildNumber = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1
val keystorePath = System.getenv("KEYSTORE_PATH")

android {
    namespace = "com.flabuis.tgmusic"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.flabuis.tgmusic"
        minSdk = 24
        targetSdk = 36
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"
        buildConfigField("int", "TG_API_ID", tgApiId.toString())
        buildConfigField("String", "TG_API_HASH", "\"$tgApiHash\"")
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    signingConfigs {
        // Same key on every CI run, so new APKs install over old ones without losing the login.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (keystorePath != null) signingConfigs.getByName("release")
                            else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.11.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.annotation:annotation:1.9.1")

    implementation("sh.calvin.reorderable:reorderable:2.4.3")

    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-session:1.5.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.9.0")
}
