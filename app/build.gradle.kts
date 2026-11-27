plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// -Pfoss  =  F-Droid build: no Google ads libraries, no ads, no limits.  Default = normal build with AdMob.
val foss = project.hasProperty("foss")

android {
    namespace = "com.pcl.chronoslock"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.pcl.chronoslock"
        minSdk = 26
        targetSdk = 34
        versionCode = 9
        versionName = "1.1.0"
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    // Release signing: the GitHub "Build Release APK" workflow supplies these from repository secrets.
    signingConfigs {
        create("release") {
            val path = System.getenv("KEYSTORE_PATH")
            if (path != null && file(path).exists()) {
                storeFile = file(path)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEYSTORE_PASSWORD")
            }
        }
    }
    sourceSets {
        getByName("main") {
            java.srcDir(if (foss) "src/foss/java" else "src/full/java")
            manifest.srcFile(if (foss) "src/main/AndroidManifest.xml" else "src/full/AndroidManifest.xml")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            if (System.getenv("KEYSTORE_PATH") != null) signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.animation:animation")
    // Ads (AdMob) + the EU/UK consent form

    implementation("androidx.core:core-ktx:1.13.1")
    // Offline speech recogniser: reads the mic directly (no audio focus), so music keeps playing
    implementation("net.java.dev.jna:jna:5.13.0@aar")
    implementation("com.alphacephei:vosk-android:0.3.47") { exclude(group = "net.java.dev.jna", module = "jna") }
}

// Ads libraries live in their own script so the F-Droid build never even reads them.
if (!foss) apply(from = "ads.gradle.kts")
