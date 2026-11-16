plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val buildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

// The official app is signed with a private key that only exists as a GitHub secret
// on the main repo. Everyone else (forks, local builds) gets "Matchday (dev)", which has
// its own app id and the public dev key, so it installs alongside the official app.
val officialKey = file("release.keystore")
val officialPassword = System.getenv("MATCHDAY_KEY_PASSWORD")
val isOfficial = officialKey.exists() && !officialPassword.isNullOrBlank()

android {
    namespace = "com.matchday.tv"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.matchday.tv"
        minSdk = 26
        targetSdk = 35
        versionCode = buildNumber
        versionName = "0.1.$buildNumber"
        if (isOfficial) {
            manifestPlaceholders["appLabel"] = "Matchday"
        } else {
            applicationIdSuffix = ".dev"
            manifestPlaceholders["appLabel"] = "Matchday (dev)"
        }
    }

    // A fixed key per variant so each new build installs over the previous one.
    signingConfigs {
        create("matchday") {
            if (isOfficial) {
                storeFile = officialKey
                storePassword = officialPassword
                keyAlias = "matchday"
                keyPassword = officialPassword
            } else {
                storeFile = file("dev.keystore")
                storePassword = "matchday"
                keyAlias = "matchday"
                keyPassword = "matchday"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("matchday")
        }
        debug {
            signingConfig = signingConfigs.getByName("matchday")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    implementation("androidx.tv:tv-material:1.0.0")

    val media3 = "1.5.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-exoplayer-hls:$media3")
    implementation("androidx.media3:media3-ui:$media3")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    implementation("io.coil-kt.coil3:coil-compose:3.0.4")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.0.4")
}
